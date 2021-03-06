package fr.lirmm.graphik.DEFT.dialectical_tree;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import fr.lirmm.graphik.DEFT.core.DefeasibleKB;
import fr.lirmm.graphik.DEFT.dialectical_tree.DialecticalTree.Node;
import fr.lirmm.graphik.DEFT.dialectical_tree.argument_preference.ArgumentPreference;
import fr.lirmm.graphik.DEFT.gad.CompactDerivation;
import fr.lirmm.graphik.DEFT.gad.Derivation;
import fr.lirmm.graphik.graal.api.core.Atom;
import fr.lirmm.graphik.graal.api.core.AtomSet;
import fr.lirmm.graphik.graal.api.core.AtomSetException;
import fr.lirmm.graphik.graal.api.core.InMemoryAtomSet;
import fr.lirmm.graphik.graal.api.core.Rule;
import fr.lirmm.graphik.graal.api.core.Substitution;
import fr.lirmm.graphik.graal.api.forward_chaining.ChaseException;
import fr.lirmm.graphik.graal.api.forward_chaining.RuleApplicationException;
import fr.lirmm.graphik.graal.api.homomorphism.HomomorphismException;
import fr.lirmm.graphik.graal.api.homomorphism.HomomorphismFactoryException;
import fr.lirmm.graphik.graal.core.DefaultConjunctiveQuery;
import fr.lirmm.graphik.graal.core.atomset.graph.DefaultInMemoryGraphStore;
import fr.lirmm.graphik.graal.homomorphism.SmartHomomorphism;
import fr.lirmm.graphik.util.stream.CloseableIterator;
import fr.lirmm.graphik.util.stream.CloseableIteratorWithoutException;
import fr.lirmm.graphik.util.stream.IteratorException;

public class ArgumentationFramework {
	public DefeasibleKB kb;
	private ArgumentPreference preferenceFunction;
	public HashMap<String, Integer> entailmentStatus;
	
	public ArgumentationFramework(DefeasibleKB kb, ArgumentPreference pref) throws IteratorException {
		this.kb = kb;
		this.preferenceFunction = pref;
		this.entailmentStatus = new HashMap<String, Integer>();
		CloseableIterator<Atom> itAtoms = this.kb.strictAtomSet.iterator();
		while(itAtoms.hasNext()) {
			this.entailmentStatus.put(itAtoms.next().toString(), DefeasibleKB.STRICTLY_ENTAILED);
		}
	}

	public void setPreferenceFunction(ArgumentPreference pref) {
		this.preferenceFunction = pref;
	}
	
	public LinkedList<Argument> getAttackersFor(Argument arg) throws AtomSetException, HomomorphismException, HomomorphismFactoryException, RuleApplicationException, ChaseException, IteratorException {
		LinkedList<Argument> attackers = new LinkedList<Argument>();
		AtomSet supportAtoms = arg.support.getAtoms();
		
		for(Rule r : kb.negativeConstraintSet) {
			CloseableIteratorWithoutException<Atom> ncIt = r.getBody().iterator();
			Atom supportAtom = null;
			Atom attackerAtom = null;
			
			// We make sure that the NC contains exactly two atoms!
			// and we suppose that the first atom of the negative constraint is used in the support, so the second atom is an attacker.
			if(ncIt.hasNext()) { supportAtom = ncIt.next(); }
			else { throw new AtomSetException("Negative Constraint does not contain any atom!"); }
			if(ncIt.hasNext()) { attackerAtom = ncIt.next(); }
			else { throw new AtomSetException("Negative Constraint only contains one atom (must contain 2 atoms)!"); }
			if(ncIt.hasNext()) { throw new AtomSetException("Negative Constraint contains more than 2 atoms!"); }
			
			// Test if the first Atom of the NC can be mapped to an atom (or atoms) in the support of the argument.
			
			// Note: DefaultConjunctiveQuery only accepts a set, so we put the atom in a set 'atomSettified'
			InMemoryAtomSet atomSettified = new DefaultInMemoryGraphStore();
			atomSettified.add(supportAtom);			
			DefaultConjunctiveQuery query = new DefaultConjunctiveQuery(atomSettified);
			
			CloseableIterator<Substitution> substitutions = SmartHomomorphism.instance().execute(query, supportAtoms);
			
			if(!substitutions.hasNext()) { // the first NC atom cannot be mapped to atoms in the support of the argument.
				// Test if the second Atom of the NC can be mapped to the support.
				// we 'switch' the variables (the second atom is now the supporting one) 
				Atom tmp = attackerAtom;
				attackerAtom = supportAtom;
				supportAtom = tmp;
				
				atomSettified = new DefaultInMemoryGraphStore();
				atomSettified.add(supportAtom);
				
				query = new DefaultConjunctiveQuery(atomSettified);
				
				substitutions = SmartHomomorphism.instance().execute(query, supportAtoms);
				
				if(!substitutions.hasNext()) { // this NC does not affect the argument because both 
					// its atoms cannot be mapped to atoms in the support of the argument.
					continue;
				}
			}
			
			
			// TODO: what if the atom in NC can be mapped to different atoms in the support? do the test for this case.
			
			while(substitutions.hasNext()) {
				Substitution sub = substitutions.next();
				//Atom groundedSupportAtom = sub.createImageOf(supportAtom);
				Atom halfGroundAttackerAtom = sub.createImageOf(attackerAtom); // some variables might still be unground if they didn't show up in the support
				
				// Find all possible substitutions for AttackerAtom
				InMemoryAtomSet halfGroundAttackerAtomSet = new DefaultInMemoryGraphStore();
				halfGroundAttackerAtomSet.add(halfGroundAttackerAtom);			
				DefaultConjunctiveQuery attackerQuery = new DefaultConjunctiveQuery(halfGroundAttackerAtomSet);
				
				CloseableIterator<Substitution> attackerSubstitutions = SmartHomomorphism.instance().execute(attackerQuery, kb.facts);
				
				while(attackerSubstitutions.hasNext()) {
					Substitution attackerSub = attackerSubstitutions.next();
					Atom groundedAttackerAtom = attackerSub.createImageOf(halfGroundAttackerAtom);
					attackers.addAll(this.getArgumentsFor(groundedAttackerAtom));
				}
			}
		}
		return attackers;
	}
	
	
	public LinkedList<Defeater> getDefeatersFor(Argument arg) throws AtomSetException, HomomorphismException, HomomorphismFactoryException, RuleApplicationException, ChaseException, IteratorException {
		LinkedList<Defeater> defeaters = new LinkedList<Defeater>();
		
		LinkedList<Argument> attackers = this.getAttackersFor(arg);
		
		for(Argument attacker : attackers) {
			int attackStatus = this.preferenceFunction.compare(attacker, arg);
			if(attackStatus != ArgumentPreference.NOT_DEFEAT) {
				defeaters.add(new Defeater(attacker, attackStatus));
			}
		}
		
		return defeaters;
	}
	
	public boolean isDefeated(Argument arg) throws AtomSetException, HomomorphismException, HomomorphismFactoryException, RuleApplicationException, ChaseException, IteratorException {
		DialecticalTree tree = computeDialecticalTreeFor(arg);
		boolean defeated = false;
		for(Node defeater : tree.defeaters) {
			if(defeater.getLabel() == DialecticalTree.UNDEFEATED) {
				defeated = true;
				break;
			}
		}
		
		return defeated;
	}
	
	public DialecticalTree computeDialecticalTreeFor(Argument arg) throws AtomSetException, HomomorphismException, HomomorphismFactoryException, RuleApplicationException, ChaseException, IteratorException {
		
		DialecticalTree tree = new DialecticalTree(arg, this.getDefeatersFor(arg));
		
		boolean defeated = false;
		Iterator<Node> it = tree.defeaters.iterator();
		while(it.hasNext() && !defeated) {
			Node defeater = it.next();
			this.computeTree(defeater);
			// label the tree
			this.labelTree(defeater);
			if(defeater.getLabel() == DialecticalTree.UNDEFEATED) {
				defeated = true;
			}
		}
		
		return tree;
	}
	
	private void computeTree(Node n) throws AtomSetException, HomomorphismException, HomomorphismFactoryException, RuleApplicationException, ChaseException, IteratorException {
		List<Defeater> defeaters = this.getDefeatersFor(n.getData().argument);
		
		if(defeaters.isEmpty()) {
			return;
		}
		
		for(Defeater defeater: defeaters) {
			// If this node is a blocking defeater, then it cannot be defeated with another blocking defeater!
			if(n.getData().defeatType == ArgumentPreference.BLOCKING_DEFEAT 
					&& defeater.defeatType == ArgumentPreference.BLOCKING_DEFEAT) {
				continue;
			} else {
				Node child = new Node(defeater);
				this.computeTree(child);
				n.addDefeater(child);
			}
		}
	}
	
	private void labelTree(Node n) {
		List<Node> children = n.getChildren();
		if(null == children || children.isEmpty()) {
			n.setLabel(DialecticalTree.UNDEFEATED);
			return;
		} 
		boolean defeated = false;
		Iterator<Node> it = children.iterator();
		while(it.hasNext() && !defeated) {
			Node child = it.next();
			if(child.getLabel() == DialecticalTree.NOT_LABELED) {
				this.labelTree(child);
			}
			if(child.getLabel() == DialecticalTree.UNDEFEATED) {
				defeated = true;
			}
		}
		if(defeated) {
			n.setLabel(DialecticalTree.DEFEATED);
		} else {
			n.setLabel(DialecticalTree.UNDEFEATED);
		}
	}
	
	
	public LinkedList<Argument> getArgumentsFor(Atom atom) throws HomomorphismException, HomomorphismFactoryException, RuleApplicationException, AtomSetException, ChaseException, IteratorException {
		LinkedList<Argument> arguments = new LinkedList<Argument>();
		LinkedList<Derivation> derivations = this.kb.getDerivationsFor(atom);
		
		for(Derivation d : derivations) {
			arguments.add(new Argument(d, atom, kb.preferenceSet));
		}
		return arguments;
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public int updateAtomEntailmentStatus(Atom a) throws IteratorException, AtomSetException, HomomorphismException, HomomorphismFactoryException, RuleApplicationException, ChaseException {
		if(this.entailmentStatus.containsKey(a.toString())) {
			return this.entailmentStatus.get(a.toString());
		}
		// get all derivation
		Iterator<CompactDerivation> itDerivations = this.kb.gad.getCompactDerivationFor(a).iterator();
		// for each derivation start with branching
		CompactDerivation derivation = null;
		int entailment = DefeasibleKB.NOT_ENTAILED;
		
		while(itDerivations.hasNext()) {
			derivation = itDerivations.next();
			boolean survives = this.survives(derivation);
			if(survives) {
				if(derivation.isDefeasible()) entailment = DefeasibleKB.DEFEASIBLY_ENTAILED;
				else {
					entailment = DefeasibleKB.STRICTLY_ENTAILED;
					break;
				}
			}
		}
		this.entailmentStatus.put(a.toString(), entailment);
		return entailment;
	}
	
	public boolean survives(CompactDerivation derivation) throws IteratorException, AtomSetException, HomomorphismException, HomomorphismFactoryException, RuleApplicationException, ChaseException {
		boolean result = true;
		CloseableIterator<Atom> itBranching = derivation.getBranchingAtoms().iterator();
		Atom a;
		while(itBranching.hasNext()) {
			a = itBranching.next();
			if(!this.entailmentStatus.containsKey(a.toString())) { // we don't know its entailment
				updateAtomEntailmentStatus(a);
			}
			if(this.entailmentStatus.get(a.toString()) == DefeasibleKB.NOT_ENTAILED) {
				return false;
			}
		}
		CloseableIterator<Atom> itAttacking = this.getAttackersFor(derivation.getNonBranchingAtoms()).iterator();
		while(itAttacking.hasNext()) {
			a = itAttacking.next();
			if(!this.entailmentStatus.containsKey(a.toString())) { // we don't know its entailment
				updateAtomEntailmentStatus(a);
			}
			if(this.entailmentStatus.get(a.toString()) == DefeasibleKB.NOT_ENTAILED) {
				return false;
			}
		}
		return result;
	}
	
	
	public AtomSet getAttackersFor(AtomSet supportAtoms) throws AtomSetException, HomomorphismException, HomomorphismFactoryException, RuleApplicationException, ChaseException, IteratorException {
		AtomSet attackers = new DefaultInMemoryGraphStore();
		
		for(Rule r : kb.negativeConstraintSet) {
			CloseableIteratorWithoutException<Atom> ncIt = r.getBody().iterator();
			Atom supportAtom = null;
			Atom attackerAtom = null;
			
			// We make sure that the NC contains exactly two atoms!
			// and we suppose that the first atom of the negative constraint is used in the support, so the second atom is an attacker.
			if(ncIt.hasNext()) { supportAtom = ncIt.next(); }
			else { throw new AtomSetException("Negative Constraint does not contain any atom!"); }
			if(ncIt.hasNext()) { attackerAtom = ncIt.next(); }
			else { throw new AtomSetException("Negative Constraint only contains one atom (must contain 2 atoms)!"); }
			if(ncIt.hasNext()) { throw new AtomSetException("Negative Constraint contains more than 2 atoms!"); }
			
			// Test if the first Atom of the NC can be mapped to an atom (or atoms) in the support of the argument.
			
			// Note: DefaultConjunctiveQuery only accepts a set, so we put the atom in a set 'atomSettified'
			InMemoryAtomSet atomSettified = new DefaultInMemoryGraphStore();
			atomSettified.add(supportAtom);			
			DefaultConjunctiveQuery query = new DefaultConjunctiveQuery(atomSettified);
			
			CloseableIterator<Substitution> substitutions = SmartHomomorphism.instance().execute(query, supportAtoms);
			
			if(!substitutions.hasNext()) { // the first NC atom cannot be mapped to atoms in the support of the argument.
				// Test if the second Atom of the NC can be mapped to the support.
				// we 'switch' the variables (the second atom is now the supporting one) 
				Atom tmp = attackerAtom;
				attackerAtom = supportAtom;
				supportAtom = tmp;
				
				atomSettified = new DefaultInMemoryGraphStore();
				atomSettified.add(supportAtom);
				
				query = new DefaultConjunctiveQuery(atomSettified);
				
				substitutions = SmartHomomorphism.instance().execute(query, supportAtoms);
				
				if(!substitutions.hasNext()) { // this NC does not affect the argument because both 
					// its atoms cannot be mapped to atoms in the support of the argument.
					continue;
				}
			}
			
			while(substitutions.hasNext()) {
				Substitution sub = substitutions.next();
				//Atom groundedSupportAtom = sub.createImageOf(supportAtom);
				Atom groundAttackerAtom = sub.createImageOf(attackerAtom); 
				//TODO: some variables might still be unground if they didn't show up in the support
				attackers.add(groundAttackerAtom);
				
			}
		}
		return attackers;
	}
}
