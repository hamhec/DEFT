package fr.lirmm.graphik.DEFT.gad;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import fr.lirmm.graphik.DEFT.core.DefeasibleRule;
import fr.lirmm.graphik.graal.api.core.Atom;
import fr.lirmm.graphik.graal.api.core.AtomSet;
import fr.lirmm.graphik.graal.api.core.AtomSetException;
import fr.lirmm.graphik.graal.api.core.ConjunctiveQuery;
import fr.lirmm.graphik.graal.api.core.InMemoryAtomSet;
import fr.lirmm.graphik.graal.api.core.Rule;
import fr.lirmm.graphik.graal.api.core.Substitution;
import fr.lirmm.graphik.graal.api.core.Term;
import fr.lirmm.graphik.graal.api.homomorphism.HomomorphismException;
import fr.lirmm.graphik.graal.core.DefaultConjunctiveQuery;
import fr.lirmm.graphik.graal.homomorphism.SmartHomomorphism;
import fr.lirmm.graphik.util.stream.CloseableIterator;
import fr.lirmm.graphik.util.stream.IteratorException;

public class GraphOfAtomDependency {
	private HashMap<String, LinkedList<GADEdge>> map;

	/* --------------------------------
	 * Constructors
	 * -------------------------------- */

	public GraphOfAtomDependency() {
		this.map = new HashMap<String, LinkedList<GADEdge>>();
	}

	public GraphOfAtomDependency(AtomSet atomset) throws IteratorException {
		this();
		this.initialise(atomset);
	}
	
	/* --------------------------------
	 * Public Methods
	 * -------------------------------- */
	public void initialise(AtomSet atomset) throws IteratorException {
		this.map.clear();
		CloseableIterator<Atom> it = atomset.iterator();
		while (it.hasNext()) {
			Atom atom = it.next();
			this.addEdge(null, atom, null, null);
		}
	}
	
	public void addEdge(GADEdge edge) {
		String targetString = edge.getTarget().toString();
		LinkedList<GADEdge> edges = this.map.get(targetString);
		if (null == edges) { // The target atom has never been tracked before
			edges = new LinkedList<GADEdge>();
			edges.add(edge);
			this.map.put(targetString, edges);
		} else { // The target atom has been 'seen' before
			String newEdge = edge.toString();
			boolean exists = false;
			for(GADEdge e : edges) {
				if(newEdge.equals(e.toString())) {
					exists = true;
					break;
				}
			}
			
			if(!exists) {
				Rule rule = edges.iterator().next().getRule();
				if(null != rule)
					edges.add(edge);
			}
		}
	}
	
	public void addEdge(AtomSet from, Atom to, Rule rule,
			Substitution substitution) {
		GADEdge edge = new GADEdge(from, to, rule, substitution);
		this.addEdge(edge);
	}
	
	public void addEdges(AtomSet from, CloseableIterator<Atom> iteratorTargets,
			Rule rule, Substitution substitution) throws IteratorException {
		while (iteratorTargets.hasNext()) {
			Atom atom = iteratorTargets.next();
			GADEdge edge = new GADEdge(from, atom, rule, substitution);
			this.addEdge(edge);
		}
	}
	
	public LinkedList<GADEdge> getInEdges(Atom atom) {
		return this.map.get(atom.toString());
	}

	public Set<Atom> getVertices() {
		Set<Atom> set = new HashSet<Atom>();
		for(LinkedList<GADEdge> list : this.map.values()) {
			set.add(list.getFirst().getTarget());
		}
		return set;
	}
	
	public boolean isFact(Atom atom) { // Checks whether the atom is a starting fact (not derived)
		List<GADEdge> edges = getInEdges(atom);
		for (GADEdge edge : edges) {
			Rule rule = edge.getRule();
			return (edge.getRule() == null) ? true : false;
		}
		return true;
	}
	
	public LinkedList<Derivation> getDerivations(Atom atom) throws IteratorException {
		LinkedList<Derivation> paths = new LinkedList<Derivation>();
		/*if (isFact(atom)) {
			return paths;
		}*/
		
		List<GADEdge> edges = getInEdges(atom);
		if(edges != null) {
			for (GADEdge edge : edges) {
				Derivation path = new Derivation(edge);
				// Old Brother
				LinkedList<Derivation> old = new LinkedList<Derivation>();
				AtomSet atoms = edge.getSources();
				if(atoms != null) {
					CloseableIterator<Atom> it = atoms.iterator();
					while (it.hasNext()) {
						Atom a = it.next();
						old = _derivationCartesianProduct(getDerivations(a), old);
					}
				}
				LinkedList<Derivation> merge = _derivationCartesianProduct(path, old);
				paths.addAll(merge);
			}
		}
		return paths;
	}
	
	public LinkedList<LinkedList<Derivation>> getDerivations(AtomSet atoms) throws IteratorException {
		LinkedList<LinkedList<Derivation>> results = new LinkedList<LinkedList<Derivation>>();
		CloseableIterator<Atom> it = atoms.iterator();
		while(it.hasNext()) {
			Atom atom = it.next();
			results.add(getDerivations(atom));
		}
		return results;
	}
	
	public LinkedList<LinkedList<LinkedList<Derivation>>> getReasoningPaths(ConjunctiveQuery query, AtomSet store) throws HomomorphismException, IteratorException {
		LinkedList<LinkedList<LinkedList<Derivation>>> results = new LinkedList<LinkedList<LinkedList<Derivation>>>();
		
		ConjunctiveQuery newquery = new DefaultConjunctiveQuery(query.getLabel(), query.getAtomSet(), new LinkedList(query.getAtomSet().getTerms(Term.Type.VARIABLE)));
		
		CloseableIterator<Substitution> substitutions = SmartHomomorphism.instance().execute(newquery, store);
		while(substitutions.hasNext()) {
			Substitution sub = substitutions.next();
			InMemoryAtomSet atoms = sub.createImageOf(query.getAtomSet());
			LinkedList<LinkedList<Derivation>> paths = getDerivations(atoms);
			results.add(paths);
		}
		return results;
	}
	
	
	public List<CompactDerivation> getCompactDerivationFor(Atom atom) throws IteratorException, AtomSetException {
		if(this.isFact(atom)) {
			return null;
		}
		
		List<CompactDerivation> derivations = new LinkedList<CompactDerivation>();
		List<GADEdge> edges = getInEdges(atom);
		if(null == edges) {
			System.out.println("/!\\ Atom with no incoming edges! This should never occur! /!\\");
			return null; // this should never occur
		}
		
		// get all edges for this atom
		Iterator<GADEdge> itEdges = edges.iterator();
		while(itEdges.hasNext()) {
			GADEdge edge = itEdges.next();
			CompactDerivation deriv = new CompactDerivation(atom);
			if(edge.getRule() instanceof DefeasibleRule) {
				deriv.setIsDefeasible(true);
			}
			// continue till we find all branching
			CloseableIterator<Atom> itSources = edge.getSources().iterator();
			while(itSources.hasNext()) {
				Atom source = itSources.next();
				followDerivation(source, deriv);
			}
			derivations.add(deriv);
		}
		
		return derivations;
	}
	
	public void followDerivation(Atom atom, CompactDerivation derivation) throws IteratorException, AtomSetException {
		LinkedList<GADEdge> edges = this.getInEdges(atom);	
		if(edges.size() > 1) { // a branching atom
			derivation.addBranchingAtom(atom);
		} else {
			derivation.addNonBranchingAtom(atom);
			// continue till we reach a fact or a branching atom
			Iterator<GADEdge> itEdges = edges.iterator();
			while(itEdges.hasNext()) {
				GADEdge edge = itEdges.next();
				if(edge.getRule() instanceof DefeasibleRule) {
					derivation.setIsDefeasible(true);
				}
				AtomSet sources = edge.getSources();
				if(null == sources) { // this is a fact
					continue;
				}
				CloseableIterator<Atom> itSources = sources.iterator();
				while(itSources.hasNext()) {
					followDerivation(itSources.next(), derivation);
				}
			}
		}
		
	}
	/* --------------------------------
	 * Private Methods
	 * -------------------------------- */
	
	private LinkedList<Derivation> _derivationCartesianProduct(
			LinkedList<Derivation> l1, LinkedList<Derivation> l2) {
		
		if(l2.isEmpty() || l2 == null) return l1;
		
		LinkedList<Derivation> l3 = new LinkedList<Derivation>();
		for (Derivation path1 : l1) {
			for (Derivation path2 : l2) {
				Derivation tmp = new Derivation(path1);
				tmp.addAll(path2);
				l3.add(tmp);
			}
			
		}

		return l3;
	}

	private LinkedList<Derivation> _derivationCartesianProduct(Derivation l1,
			LinkedList<Derivation> l2) {
		LinkedList<Derivation> l3 = new LinkedList<Derivation>();
		l3.add(l1);
		return _derivationCartesianProduct(l3, l2);
	}
}
