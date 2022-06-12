package edu.wisc.cs.will.FOPC;

import edu.wisc.cs.will.Utils.Utils;

import java.util.Collection;

/*
 * @author shavlik
 */
public abstract class QuantifiedSentence extends Sentence {
	public  Collection<Variable> variables;
	public  Sentence             body;

	QuantifiedSentence() {}

	@Override
	public boolean containsTermAsSentence() {
		return body.containsTermAsSentence();
	}	


    @Override
	public Collection<Variable> collectAllVariables() {
		Collection<Variable>  free = collectFreeVariables(null);
		return Variable.combineSetsOfVariables(free, variables);
	}
    @Override
	public Collection<Variable> collectFreeVariables(Collection<Variable> boundVariables) {
		Collection<Variable> newBoundVars = (boundVariables == null
												? variables
												: Variable.combineSetsOfVariables(variables, boundVariables));
		
		return body.collectFreeVariables(newBoundVars);
	}

    @Override
    public BindingList isEquivalentUptoVariableRenaming(Sentence that, BindingList bindings) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

	@Override
	public boolean containsVariables() {
		return body.containsVariables(); // Should be true or no need for the variables!
	}
	
    @Override
	protected Sentence distributeDisjunctionOverConjunction() {
		Utils.error("Should not have any quantified sentences at this point of clause conversion: " + this);
		return null;
	}

    @Override
    protected Sentence distributeConjunctionOverDisjunction() {
        Utils.error("Should not have any quantified sentences at this point of clause conversion: " + this);
		return null;
    }

	@Override
	public int countVarOccurrencesInFOPC(Variable v) {
		int total = 0;
		if (variables != null && variables.contains(v)) { total += 1; }  // Not sure if we should count this, but probably good to not make these anonymous.
		if (body != null) { total += body.countVarOccurrencesInFOPC(v); } 
		return total;
	}
}
