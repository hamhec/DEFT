package fr.lirmm.graphik.DEFT.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.lirmm.graphik.DEFT.dialectical_tree.Argument;
import fr.lirmm.graphik.DEFT.dialectical_tree.ArgumentationFramework;
import fr.lirmm.graphik.DEFT.dialectical_tree.argument_preference.ArgumentPreference;
import fr.lirmm.graphik.DEFT.dialectical_tree.argument_preference.GeneralizedSpecificityArgumentPreference;
import fr.lirmm.graphik.DEFT.dialectical_tree.argument_preference.OrderingBasedArgumentPreference;
import fr.lirmm.graphik.DEFT.gad.Derivation;
import fr.lirmm.graphik.DEFT.gad.GADEdge;
import fr.lirmm.graphik.DEFT.gad.GADRuleApplicationHandler;
import fr.lirmm.graphik.DEFT.gad.GraphOfAtomDependency;
import fr.lirmm.graphik.DEFT.io.DlgpDEFTParser;
import fr.lirmm.graphik.graal.api.core.Atom;
import fr.lirmm.graphik.graal.api.core.AtomSet;
import fr.lirmm.graphik.graal.api.core.AtomSetException;
import fr.lirmm.graphik.graal.api.core.ConjunctiveQuery;
import fr.lirmm.graphik.graal.api.core.InMemoryAtomSet;
import fr.lirmm.graphik.graal.api.core.NegativeConstraint;
import fr.lirmm.graphik.graal.api.core.Rule;
import fr.lirmm.graphik.graal.api.core.RuleSet;
import fr.lirmm.graphik.graal.api.core.Substitution;
import fr.lirmm.graphik.graal.api.core.Term;
import fr.lirmm.graphik.graal.api.forward_chaining.Chase;
import fr.lirmm.graphik.graal.api.forward_chaining.ChaseException;
import fr.lirmm.graphik.graal.api.forward_chaining.RuleApplicationException;
import fr.lirmm.graphik.graal.api.homomorphism.HomomorphismException;
import fr.lirmm.graphik.graal.api.homomorphism.HomomorphismFactoryException;
import fr.lirmm.graphik.graal.api.io.ParseException;
import fr.lirmm.graphik.graal.core.DefaultConjunctiveQuery;
import fr.lirmm.graphik.graal.core.atomset.graph.DefaultInMemoryGraphStore;
//import fr.lirmm.graphik.graal.core.atomset.graph.DefaultInMemoryGraphAtomSet;
import fr.lirmm.graphik.graal.core.ruleset.LinkedListRuleSet;
import fr.lirmm.graphik.graal.forward_chaining.SccChase;
import fr.lirmm.graphik.graal.homomorphism.SmartHomomorphism;
import fr.lirmm.graphik.graal.io.dlp.DlgpParser;
import fr.lirmm.graphik.util.stream.CloseableIterator;
import fr.lirmm.graphik.util.stream.IteratorException;

/**
 * This class represents the Knowledge Base, it contains strict and defeasible
 * atoms and rules and offers methods to run queries and find derivations for atoms.
 * 
 * @author Abdelraouf Hecham (INRIA) <hecham.abdelraouf@gmail.com>
 */
public class DefeasibleKB {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(DefeasibleKB.class);
	
	public static final int NOT_ENTAILED = 0;
	public static final int STRICTLY_ENTAILED = 1;
	public static final int DEFEASIBLY_ENTAILED = 2;

	public RuleSet strictRuleSet;
	public RuleSet defeasibleRuleSet;
	public RuleSet negativeConstraintSet;
	public RuleSet rules;
	
	public AtomSet strictAtomSet;
	public AtomSet defeasibleAtomSet;
	public AtomSet facts;
	
	public PreferenceSet preferenceSet;
	
	/**
	 * Graph of Atom Dependency allows us to extract all possible Derivations for atoms.
	 */
	public GraphOfAtomDependency gad;
	
	/**
	 * Argumentation framework to get Attackers, Defeaters, DialecticalTree...etc.
	 */
	public ArgumentationFramework af;
	
	// /////////////////////////////////////////////////////////////////////////
	// CONSTRUCTORS
	// /////////////////////////////////////////////////////////////////////////
	
	/**
	 * Simple constructor, creates an empty knowledge base.
	 * @throws IteratorException 
	 */
	public DefeasibleKB() throws IteratorException {
		// Everything is initialized to empty.
		this.strictAtomSet = new DefaultInMemoryGraphStore();
		this.defeasibleAtomSet = new DefaultInMemoryGraphStore();
		this.facts = new DefaultInMemoryGraphStore();

		this.strictRuleSet = new LinkedListRuleSet();
		this.defeasibleRuleSet = new LinkedListRuleSet();
		this.rules = new LinkedListRuleSet();

		this.negativeConstraintSet = new LinkedListRuleSet();
		
		this.preferenceSet = new PreferenceSet();
		
		this.gad = new GraphOfAtomDependency();

		//this.af = new ArgumentationFramework(this, new GeneralizedSpecificityArgumentPreference());
		//this.af = new ArgumentationFramework(this, new OrderingBasedArgumentPreference());
		this.af = new ArgumentationFramework(this, new OrderingBasedArgumentPreference(new GeneralizedSpecificityArgumentPreference()));
	}
	
	/**
	 * Creates a knowledge base from a stored DLGP file using the file's path string.
	 * @throws IteratorException 
	 */
	public DefeasibleKB(String file) throws FileNotFoundException, AtomSetException, IteratorException {
		this(new File(file));
	}
	
	/**
	 * Creates a knowledge base from a DLGP file.
	 * @throws IteratorException 
	 */
	public DefeasibleKB(File file) throws FileNotFoundException, AtomSetException, IteratorException {
		this(new FileReader(file));
	}
	
	/**
	 * Creates a knowledge base from a DLGP file.
	 * @throws IteratorException 
	 */
	public DefeasibleKB(Reader reader) throws FileNotFoundException, AtomSetException, IteratorException {
		this();
		this.add(reader);
	}
	// /////////////////////////////////////////////////////////////////////////
	// PUBLIC METHODS
	// /////////////////////////////////////////////////////////////////////////
	/**
	 * Sets the preference function to use.
	 */
	public void setPreferenceFunction(ArgumentPreference pref) {
		this.af.setPreferenceFunction(pref);
	}
	
	/*
	 * Initializes the GAD of the Knowledge Base, with the facts.
	 */
	public void initialise() throws IteratorException {
		this.gad.initialise(this.facts);
	}
	
	/**
	 * Parses a String and Adds the element (Atom, Rule, NegativeConstraint...).
	 */
	public void add(Reader reader) {
		// Get a dlgp Parser made for DEFT (takes into account DEFT annotations).
		DlgpDEFTParser dlgpParser = new DlgpDEFTParser(reader);
		while (dlgpParser.hasNext()) {
			Object o = dlgpParser.next();
			if (o instanceof Atom) {
				this.addAtom((Atom) o);
			} else if (o instanceof NegativeConstraint) {
				this.addNegativeConstraint((Rule) o);
			} else if (o instanceof Rule) {
				this.addRule((Rule) o);
			}  else if(o instanceof Preference) {
				this.addPreference((Preference) o);
			}
		}
	}
	
	/**
	 * Parses a String and Adds the element (Atom, Rule, NegativeConstraint...).
	 */
	public void add(String str) {
		this.add(new StringReader(str));
	}
	
	/**
	 * Adds an Atom to the knowledge base, if the Atom is an instance of DefeasibleAtom
	 * it is added to defeasibleAtomSet, else it is added to strictAtomSet.
	 */
	public void addAtom(Atom atom) {
		try {
			if (atom instanceof DefeasibleAtom) {
				this.defeasibleAtomSet.add(atom);
			} else {
				this.strictAtomSet.add(atom);
			}
			this.facts.add(atom);
		} catch (AtomSetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Adds an Atom to the knowledge base after parsing it from a String.
	 */
	public void addAtom(String atomString) {
		// Get a dlgp Parser made for DEFT (takes into account DEFT annotations).
		Atom atom = DlgpDEFTParser.parseAtom(atomString);
		
		this.addAtom(atom);
	}
	
	/**
	 * Adds a Rule to the knowledge base, if the Rule is an instance of DefeasibleRule
	 * it is added to defeasibleRuleSet, else it is added to strictRuleSet.
	 */
	public void addRule(Rule rule) {
		try {
			if (rule instanceof DefeasibleRule) {
				this.defeasibleRuleSet.add(rule);
			} else {
				this.strictRuleSet.add(rule);
			}

			this.rules.add(rule);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Adds a Rule to the knowledge base after parsing it from a String.
	 */
	public void addRule(String ruleString) {
		// Get a dlgp Parser made for DEFT (takes into account DEFT annotations).
		Rule rule = DlgpDEFTParser.parseRule(ruleString);
	
		this.addRule(rule);
	}
	
	/**
	 * Adds a Negative constraint to the knowledge base.
	 */
	public void addNegativeConstraint(Rule nc) {
		this.negativeConstraintSet.add(nc);
	}
	
	/**
	 * Adds a Negative constraint to the knowledge base after parsing it from a String.
	 */
	public void addNegativeConstraint(String ncString) {
		// Get a dlgp Parser made for DEFT (takes into account DEFT annotations).
		Rule nc = DlgpDEFTParser.parseNegativeConstraint(ncString);
		this.addNegativeConstraint(nc);
	}
	
	/**
	 * Adds a Preference to the knowledge base.
	 */
	public void addPreference(Preference preference) {
		this.preferenceSet.add(preference);
	}
	
	/**
	 * Adds a Preference to the knowledge base after parsing it from a String.
	 */
	public void addPreference(String preferenceString) {
		Preference preference = DlgpDEFTParser.parsePreference(preferenceString);
		this.addPreference(preference);
	}
	
	
	/**
	 * Cleans the KB then applies all rules (strict and defeasible) using a chase and adds new facts
	 * to the facts set.
	 * @throws ChaseException, AtomSetException 
	 * @throws IteratorException 
	 */
	public void saturate() throws ChaseException, AtomSetException, IteratorException {
		this.unsaturate();
		this.initialise();
		this.saturateWithoutCleaning();
	}
	
	/**
	 * Applies all rules (strict and defeasible) using a chase and adds new facts
	 * to the facts set without prior cleaning of previously deduced facts.
	 * @throws ChaseException 
	 */
	public void saturateWithoutCleaning() throws ChaseException {
		Chase chase = new SccChase<AtomSet>(this.rules.iterator(), this.facts,
				new GADRuleApplicationHandler(this.gad).getRuleApplier());
		chase.execute();
	}
	
	/**
	 * Applies all rules (strict and defeasible) along with all NegativeConstraints using a chase and adds new facts
	 * to the facts set.
	 */
	public void saturateWithNegativeConstraint() throws ChaseException {
		RuleSet rulesWithNc = new LinkedListRuleSet();
		rulesWithNc.addAll(this.rules.iterator());
		rulesWithNc.addAll(this.negativeConstraintSet.iterator());
		
		Chase chase = new SccChase<AtomSet>(rulesWithNc.iterator(), this.facts,
				new GADRuleApplicationHandler(this.gad).getRuleApplier());
		chase.execute();
	}
	
	/**
	 * Reverts back to the initial knowledge base, without any 'new' deduced facts.
	 */
	public void unsaturate() throws AtomSetException {
		this.facts.clear();
		this.facts.addAll(this.strictAtomSet);
		this.facts.addAll(this.defeasibleAtomSet);
	}
	
	/**
	 * Query the knowledge base using a dlgp query string, e.g.: "? :- p(a)."
	 * @param queryString This is the string representing the query in dlgp format.
	 * @return CloseableIterator<Substitution> Iterator over the possible substitutions that satisfies the query.
	 * @throws ParseException Problem Parsing Query
	 * @exception HomomorphismException .
	 */
	public CloseableIterator<Substitution> query(String queryString)
			throws HomomorphismException, ParseException {
		ConjunctiveQuery query = DlgpParser.parseQuery(queryString);
		return this.query(query);
	}
	
	/**
	 * Query the knowledge base using a conjunctiveQuery object.
	 * @param query This is the conjunctiveQuery object.
	 * @return CloseableIterator<Substitution> Iterator over the possible substitutions that satisfies the query.
	 * @exception HomomorphismException .
	 */
	public CloseableIterator<Substitution> query(ConjunctiveQuery query)
			throws HomomorphismException {
		DefaultConjunctiveQuery newquery = new DefaultConjunctiveQuery(
				query.getLabel(), query.getAtomSet(), new LinkedList<Term>(
						query.getAtomSet().getTerms(Term.Type.VARIABLE)));
		// Atoms that match the query
		CloseableIterator<Substitution> substitutions = SmartHomomorphism
				.instance().execute(newquery, this.facts);

		return substitutions;
	}
	
	/**
	 * Gets all the atoms that satisfy an Atomic query, it transforms the substitutions
	 * obtained using the method query(String queryString) to actual atoms.
	 * @param q This is the string representing the query in dlgp format.
	 * @return AtomSet The sets of atoms that satisfy the query.
	 * @exception HomomorphismException
	 * @throws IteratorException 
	 */
	public AtomSet getAtomsSatisfiyingAtomicQuery(String q)
			throws HomomorphismException, IteratorException {
		ConjunctiveQuery query = DlgpParser.parseQuery(q);

		InMemoryAtomSet results = new DefaultInMemoryGraphStore();
		CloseableIterator<Substitution> substitutions = this.query(query);

		while (substitutions.hasNext()) {
			Substitution sub = substitutions.next();
			InMemoryAtomSet atoms = sub.createImageOf(query.getAtomSet());
			results.add(atoms.iterator().next());
		}

		return results;
	}
	
	/**
	 * Gets all possible derivations for an Atom.
	 * @throws IteratorException 
	 */
	public LinkedList<Derivation> getDerivationsFor(Atom atom)
			throws HomomorphismException, HomomorphismFactoryException,
			RuleApplicationException, AtomSetException, ChaseException, IteratorException {
		LinkedList<Derivation> derivations = this.gad.getDerivations(atom);
		LinkedList<Derivation> acceptable_derivations = new LinkedList<Derivation>();

		for (Derivation d : derivations) {
			if (d.isConsistent(this.strictRuleSet, this.negativeConstraintSet)) {
				acceptable_derivations.add(d);
			}
		}
		return acceptable_derivations;
	}
	
	/**
	 * Gets all possible derivations for an Atom expressed in dlgp string format.
	 * @throws IteratorException 
	 */
	public LinkedList<Derivation> getDerivationsFor(String atomString)
			throws HomomorphismException, HomomorphismFactoryException,
			RuleApplicationException, AtomSetException, ChaseException, IteratorException {
		Atom atom = DlgpParser.parseAtom(atomString);
		return this.getDerivationsFor(atom);
	}

	/**
	 * Returns an integer representing the entailment status of an Atom. This status can either be 
	 * not entailed (0), strictly(1) or defeasibly(2) entailed.
	 * @param atom This is an Atom object.
	 * @return int Representing entailement status.
	 * @throws IteratorException 
	 */
	public int EntailmentStatus(Atom atom) throws AtomSetException,
			HomomorphismException, HomomorphismFactoryException,
			RuleApplicationException, ChaseException, IteratorException {
		
		int status = DefeasibleKB.NOT_ENTAILED;

		LinkedList<Derivation> derivations = this.getDerivationsFor(atom);

		List<Argument> arguments = new LinkedList<Argument>();

		for (Derivation d : derivations) {
			if(LOGGER.isInfoEnabled()) {
				LOGGER.info("Derivation :" + d.toString());
			}
			
			arguments.add(new Argument(d, atom, this.preferenceSet));
		}

		for (Argument arg : arguments) {
			if (!af.isDefeated(arg)) {
				if (arg.support.isDefeasible()) {
					status = DefeasibleKB.DEFEASIBLY_ENTAILED;
				} else {
					status = DefeasibleKB.STRICTLY_ENTAILED; 
					// No need to check other arguments supporting this atom.
					break;
				}
			}
		}
		return status;
	}
	
	
	public int getEntailmentStatus(Atom atom) {
		int status = DefeasibleKB.NOT_ENTAILED;
		
		return status;
	}
	/**
	 * Displays the facts of this Knowledge base as a string.
	 */
	public String toString() {
		StringBuilder s = new StringBuilder();
		CloseableIterator<Atom> it = this.facts.iterator();
		try {
			while(it.hasNext()) {
				Atom atom = it.next();
				s.append(atom + "\n");
			}
		} catch (IteratorException e) {
			return "Exception: " + e.getMessage();
		}
		return s.toString();
	}
}
