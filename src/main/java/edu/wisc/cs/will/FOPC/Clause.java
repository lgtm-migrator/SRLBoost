package edu.wisc.cs.will.FOPC;

import edu.wisc.cs.will.FOPC.visitors.SentenceVisitor;
import edu.wisc.cs.will.Utils.Utils;

import java.util.*;

/*
 * @author shavlik
 */
public class Clause extends Sentence implements DefiniteClause {
	static final int defaultNumberOfLiteralsPerRowInPrintouts = 1;
	private static final int maxLiteralsToPrint = 100; // This maximum applies INDEPENDENTLY to both the positive and negative literals.
	
	public  List<Literal> posLiterals;
	public  List<Literal> negLiterals;

	private Boolean       bodyContainsCut = null; // This is now a tristate-variable.  True/False mean the normal thing.  Null means not yet evaluated.

	private String        extraLabel      = null; // This is partially implemented so that we can take extraLabels from SingleClauseNodes and have them persist when clauses are created.  However, weight until we are sure we want the overhead of doing this, plus if a comment should be printed inside a comment, we might have parser problems.
	public String getExtraLabel()                  {	return extraLabel; }

    protected Clause() {
    }
    
	public Clause(HandleFOPCstrings stringHandler, List<Literal> posLiterals, List<Literal> negLiterals) { // If called this, there is no error checking to confirm 'sign' of literals. This is done to save cpu time.
    	this();
		this.stringHandler = stringHandler;
		this.posLiterals   = posLiterals;
		this.negLiterals   = negLiterals;
	}
	public Clause(HandleFOPCstrings stringHandler, List<Literal> posLiterals, List<Literal> negLiterals, String extraLabel) {
		this(stringHandler, posLiterals, negLiterals);
		this.extraLabel = extraLabel;
	}
	protected Clause(HandleFOPCstrings stringHandler, Clause other)  {
    	this();
		this.stringHandler = stringHandler;
		this.posLiterals   = other.posLiterals;
		this.negLiterals   = other.negLiterals;
	}

	Clause(HandleFOPCstrings stringHandler, List<Literal> literals, boolean literalsAreAllPos) { // If not all positive, assumes all are negative.
    	this();
		this.stringHandler = stringHandler;
		if (literalsAreAllPos) {
			posLiterals = literals;
			negLiterals = null;
		}
		else {
			posLiterals = null;
			negLiterals = literals;
		}
	}
	Clause(HandleFOPCstrings stringHandler, Literal literal, boolean literalIsPos) {
    	this();
		this.stringHandler = stringHandler;
		if (literalIsPos) {
			posLiterals = new ArrayList<>(1);
			posLiterals.add(literal);
			negLiterals = null;
		}
		else{
			negLiterals = new ArrayList<>(1);
			negLiterals.add(literal);
			posLiterals = null;
		}
	}	
	public Clause(HandleFOPCstrings stringHandler, Literal literal,	boolean literalIsPos, String extraLabel) {
		this(stringHandler, literal, literalIsPos);
		this.extraLabel = extraLabel;
	}

    void addNegLiteralToFront(Literal lit) {
		if (negLiterals == null) { negLiterals = new ArrayList<>(1); }
		negLiterals.add(0, lit);
	}

	// These allow a single FOR LOOP to walk through all the literals.
	public int getLength() {
		return Utils.getSizeSafely(posLiterals) + Utils.getSizeSafely(negLiterals);
	}

    public Literal getPosLiteral(int i) {
        if ( posLiterals == null ) throw new IndexOutOfBoundsException();
        return posLiterals.get(i);
    }

    public int getPosLiteralCount() {
        return posLiterals == null ? 0 : posLiterals.size();
    }

    public Literal getNegLiteral(int i) {
        if ( negLiterals == null ) throw new IndexOutOfBoundsException();
        return negLiterals.get(i);
    }

    public int getNegLiteralCount() {
        return negLiterals == null ? 0 : negLiterals.size();
    }

    /* Returns the list of positive literals with gaurantee of being non-null.
     *
     * @return Non-null list of Positive literals.
     */
    public List<Literal> getPositiveLiterals() {
        if ( posLiterals == null ) return Collections.EMPTY_LIST;
        else return posLiterals;
    }

    /* Returns the list of negative literals with gaurantee of being non-null.
     *
     * @return Non-null list of negative literals.
     */
    public List<Literal> getNegativeLiterals() {
        if ( negLiterals == null ) return Collections.EMPTY_LIST;
        else return negLiterals;
    }

	/*
	 * Would any variables in this clause remain UNBOUND if this binding list were to be applied?
	 */
    @Override
	public boolean containsFreeVariablesAfterSubstitution(BindingList theta) {
		if (posLiterals != null) { for (Literal litP : posLiterals) if (litP.containsFreeVariablesAfterSubstitution(theta)) { return true; } }
		if (negLiterals != null) { for (Literal litN : negLiterals) if (litN.containsFreeVariablesAfterSubstitution(theta)) { return true; } }
		return false;
	}	
	
	void checkForCut() {
		if ( bodyContainsCut == null ) {

            boolean found = false;
            if (negLiterals != null) { // Mark that is clause contains a 'cut' - this info is needed at the time the head (i.e., the positive literal) is matched and we don't want to check everything a clause is used in resolution theorem proving.
                for (Literal lit : negLiterals) if (lit.predicateName.name.equals("!")) {
                    found = true;
                    break;
                }
            }

            bodyContainsCut = found;
        }
	}
	
	// Could make this a subclass, but this seems fine.
    @Override
	public boolean isDefiniteClause() { // A disjunction of ONE positive and any number of negated literals is DEFINITE.  See http://en.wikipedia.org/wiki/Horn_clause
		return getPosLiteralCount() == 1;
	}

    @Override
    public boolean isDefiniteClauseRule() {
        return getPosLiteralCount() == 1 && getNegLiteralCount() > 0;
    }

    @Override
	public boolean isDefiniteClauseFact() { // A disjunction of ONE positive and any number of negated literals is DEFINITE.  See http://en.wikipedia.org/wiki/Horn_clause
		return getPosLiteralCount() == 1 && getNegLiteralCount() == 0;
	}

    @Override
    public Literal getDefiniteClauseHead() throws IllegalStateException{
        if (!isDefiniteClause()) throw new IllegalStateException("Clause '" + this + "' is not a definite clause.");
        return posLiterals.get(0);
    }

    @Override
    public Literal getDefiniteClauseFactAsLiteral() throws IllegalStateException {
        if (!isDefiniteClauseFact()) throw new IllegalStateException("Clause '" + this + "' is not a definite clause fact.");
        return posLiterals.get(0);
    }

    @Override
    public Clause getDefiniteClauseAsClause() throws IllegalStateException {
        if (!isDefiniteClause()) throw new IllegalStateException("Clause '" + this + "' is not a definite clause.");
        return this;
    }

    @Override
    public List<Literal> getDefiniteClauseBody() {
        return getNegativeLiterals();
    }

    public BindingList unifyDefiniteClause(DefiniteClause otherDefiniteClause, BindingList bindingList) {
        if ( this.isDefiniteClauseRule() != otherDefiniteClause.isDefiniteClauseRule() ) {
            return null;
        }

        Clause otherClause = otherDefiniteClause.getDefiniteClauseAsClause();

        return unify(otherClause, bindingList);

    }

    private BindingList unify(Clause that, BindingList bindingList) {
        if ( this.getPosLiteralCount() != that.getPosLiteralCount() || this.getNegLiteralCount() != that.getNegLiteralCount() ) {
            return null;
        }

        if ( bindingList == null ) bindingList = new BindingList();

        if ( this == that ) return bindingList;

        for (int i = 0; i < getPosLiteralCount(); i++) {
            bindingList = Unifier.UNIFIER.unify(this.getPosLiteral(i), that.getPosLiteral(i), bindingList);
            if ( bindingList == null ) return null;
        }

        for (int i = 0; i < getNegLiteralCount(); i++) {
            bindingList = Unifier.UNIFIER.unify(this.getNegLiteral(i), that.getNegLiteral(i), bindingList);
            if ( bindingList == null ) return null;
        }

        return bindingList;
    }

	public boolean isEmptyClause() {
		return getPosLiteralCount() == 0 && getNegLiteralCount() == 0;
	}

	@Override
	public Clause applyTheta(Map<Variable,Term> theta) {
		List<Literal> newPosLiterals = null;
		List<Literal> newNegLiterals = null;
		
		if (posLiterals != null) {
			newPosLiterals = new ArrayList<>(posLiterals.size());
			for (Literal lit : posLiterals) { newPosLiterals.add(lit.applyTheta(theta)); }
		}
		if (negLiterals != null) {
			newNegLiterals = new ArrayList<>(negLiterals.size());
			for (Literal lit : negLiterals) { newNegLiterals.add(lit.applyTheta(theta)); }
		}

		return (Clause) stringHandler.getClause(newPosLiterals, newNegLiterals, extraLabel).setWeightOnSentence(wgtSentence);
	}

    public Clause applyTheta(BindingList bindingList) {
        if ( bindingList != null ) {
            return applyTheta(bindingList.theta);
        }
        else {
            return this;
        }
    }

    @Override
    public BindingList isEquivalentUptoVariableRenaming(Sentence that, BindingList bindings) {
        if (!(that instanceof Clause)) return null;

        Clause thatClause = (Clause) that;

        if ( this.getPosLiteralCount() != thatClause.getPosLiteralCount() ) return null;
        if ( this.getNegLiteralCount() != thatClause.getNegLiteralCount() ) return null;

        if ( bindings == null ) bindings = new BindingList();

        for (int i = 0; i < getPosLiteralCount(); i++) {
            bindings = this.getPosLiteral(i).isEquivalentUptoVariableRenaming(thatClause.getPosLiteral(i), bindings);
            if ( bindings == null ) return null;
        }

        for (int i = 0; i < getNegLiteralCount(); i++) {
            bindings = this.getNegLiteral(i).isEquivalentUptoVariableRenaming(thatClause.getNegLiteral(i), bindings);
            if ( bindings == null ) return null;
        }

        return bindings;
    }

    @Override
	public Clause copy(boolean recursiveCopy) {
		List<Literal> newPosLiterals = (posLiterals == null ? null : new ArrayList<>(posLiterals.size()));
		List<Literal> newNegLiterals = (negLiterals == null ? null : new ArrayList<>(negLiterals.size()));
		
		if (recursiveCopy) {
            if (posLiterals != null) {
                for (Literal p : posLiterals) {
                    newPosLiterals.add(p.copy(true));
                }
            }
            if (negLiterals != null) {
                for (Literal n : negLiterals) {
                    newNegLiterals.add(n.copy(true));
                }
            }
			Clause newClause = (Clause) stringHandler.getClause(newPosLiterals, newNegLiterals, extraLabel).setWeightOnSentence(wgtSentence);
			newClause.setBodyContainsCut(getBodyContainsCut());	
			return newClause;
		}
		if (posLiterals != null) { newPosLiterals.addAll(posLiterals); }
		if (negLiterals != null) { newNegLiterals.addAll(negLiterals); }
		Clause newClause = (Clause) stringHandler.getClause(newPosLiterals, newNegLiterals, extraLabel).setWeightOnSentence(wgtSentence);
		newClause.setBodyContainsCut(getBodyContainsCut());
		return newClause;
	}

    @Override
	public Clause copy2(boolean recursiveCopy, BindingList bindingList) {
		List<Literal> newPosLiterals = (posLiterals == null ? null : new ArrayList<>(posLiterals.size()));
		List<Literal> newNegLiterals = (negLiterals == null ? null : new ArrayList<>(negLiterals.size()));

		if (recursiveCopy) {
            if (posLiterals != null) {
                for (Literal p : posLiterals) {
                    newPosLiterals.add(p.copy2(true, bindingList));
                }
            }
            if (negLiterals != null) {
                for (Literal n : negLiterals) {
                    newNegLiterals.add(n.copy2(true, bindingList));
                }
            }
			return (Clause) stringHandler.getClause(newPosLiterals, newNegLiterals).setWeightOnSentence(wgtSentence);
		}
		if (posLiterals != null) { newPosLiterals.addAll(posLiterals); }
		if (negLiterals != null) { newNegLiterals.addAll(negLiterals); }
		return (Clause) stringHandler.getClause(newPosLiterals, newNegLiterals).setWeightOnSentence(wgtSentence);
	}

    @Override
    public List<Clause> convertToClausalForm() {
		List<Clause> listClause = new ArrayList<>(1);

        Clause clause = this;
        listClause.add(clause);
        return listClause;
    }
	

	public BindingList copyAndReplaceVariablesWithNumbers(StringConstant[] constantsToUse) {
		Collection<Variable> collectedFreeVariables = collectFreeVariables(null);
		if (collectedFreeVariables == null) { return null; }
		BindingList bl = new BindingList();
		int counter = 0;
		int numberOfConstants = constantsToUse.length;
		for (Variable var : collectedFreeVariables) {
			StringConstant nextConstant = (counter >= numberOfConstants
											? stringHandler.getStringConstant("WillConstant" + (++counter)) // Recall that these count from ONE.
											: constantsToUse[counter++]);
			bl.addBinding(var, nextConstant);
		}
		return bl;
	}
	
    @Override
	public boolean containsTermAsSentence() {
		return false;
	}

    @Override
	public Collection<Variable> collectAllVariables() {
		return collectFreeVariables(null);
	}

    @Override
    public Collection<Variable> collectFreeVariables(Collection<Variable> boundVariables) {

		List<Variable>  result = null;
		
		if (posLiterals != null) for (Literal lit : posLiterals) {
			Collection<Variable> temp = lit.collectFreeVariables(boundVariables);
			
			if (temp != null) for (Variable var : temp) if (result == null || !result.contains(var)) {
				if (result == null) { result = new ArrayList<>(4); } // Wait to create until needed.
				result.add(var);
			}	
		}
		if (negLiterals != null) for (Literal lit : negLiterals) {
			Collection<Variable> temp = lit.collectFreeVariables(boundVariables);
			
			if (temp != null) for (Variable var : temp) if (result == null || !result.contains(var)) {
				if (result == null) { result = new ArrayList<>(4); } // Wait to create until needed.
				result.add(var);
			}		
		}						
		return result == null ? Collections.EMPTY_LIST : result;
	}
	@Override
	public int hashCode() {
        return super.hashCode();
    }
    @Override
	public boolean equals(Object other) { // TODO doesn't deal with permutations in the literals.  Not sure doing so is necessary; other code deals with canonical forms.
		if (this == other) { return true; }
        if (!(other instanceof Clause)) { return false; }
		Clause otherAsClause = (Clause) other;
		
		if (posLiterals != null) {
			if (otherAsClause.posLiterals == null) { return false; }
			if (posLiterals.size() != otherAsClause.posLiterals.size()) { return false; }
			for (int i = 0; i < posLiterals.size(); i++) {
				if (!posLiterals.get(i).equals(otherAsClause.posLiterals.get(i))) { return false; }
			}
		}
		if (negLiterals != null) {
			if (otherAsClause.negLiterals == null) { return false; }
			if (negLiterals.size() != otherAsClause.negLiterals.size()) { return false; }
			for (int i = 0; i < negLiterals.size(); i++) {
				if (!negLiterals.get(i).equals(otherAsClause.negLiterals.get(i))) { return false; }
			}
		}
		return true;
	}

    @Override
	public BindingList variants(Sentence other, BindingList bindings) { 
	
        // We would really like to lazily create this if null, but that would
        // require a rewrite of all the other variant code...maybe later.
        if ( bindings == null ) bindings = new BindingList();

        if ( this == other ) {
            return bindings;
        }
        else if (other instanceof Clause) {
            Clause that = (Clause) other;

            if ( this.getPosLiteralCount() != that.getPosLiteralCount() || this.getNegLiteralCount() != that.getNegLiteralCount() ) {
                return null;
            }
            
            for (int i = 0; i < getPosLiteralCount(); i++) {
                bindings = this.getPosLiteral(i).variants(that.getPosLiteral(i), bindings);
                if ( bindings == null ) {
                    return null;
                }
            }


            for (int i = 0; i < getNegLiteralCount(); i++) {
                bindings = this.getNegLiteral(i).variants(that.getNegLiteral(i), bindings);
                if ( bindings == null ) {
                    return null;
                }
            }

            return bindings;
        }
        else {
            return null;
        }
	}
	
    @Override
	public boolean containsVariables() {
		if (posLiterals != null) for (Literal lit : posLiterals) if (lit.containsVariables()) { return true; }
		if (negLiterals != null) for (Literal lit : negLiterals) if (lit.containsVariables()) { return true; }
		return false;
	}
	
	// Clauses are already in clausal form, so no need to convert them.
    @Override
	protected boolean containsThisFOPCtype(String marker) {
		return false;
	}
    @Override
	protected Clause convertEquivalenceToImplication() {
		return this;
	}
    @Override
	protected Sentence eliminateImplications() {

        Sentence sentenceA = null;
        if (posLiterals != null) {
            for (Literal literal : posLiterals) {

                if (sentenceA == null) {
                    sentenceA = literal;
                }
                else {
                    sentenceA = stringHandler.getConnectedSentence(sentenceA, ConnectiveName.AND, literal);
                }
            }
        }

        Sentence sentenceB = null;
        if (negLiterals != null) {
            for (Literal literal : negLiterals) {

                Sentence notLiteral = stringHandler.getConnectedSentence(ConnectiveName.NOT, literal);

                if (sentenceB == null) {
                    sentenceB = literal;
                }
                else {
                    sentenceB = stringHandler.getConnectedSentence(sentenceB, ConnectiveName.OR, notLiteral);
                }
            }
        }

        if ( sentenceA != null && sentenceB != null ) {
            return stringHandler.getConnectedSentence(sentenceA, ConnectiveName.OR, sentenceB );
        }
        else if ( sentenceB != null ) {
            return sentenceB;
        }
        else {
            return sentenceA;
        }
	}
    @Override
	protected Sentence negate() {
		
        Sentence negation = null;

        if (posLiterals != null) {
            for (Literal literal : posLiterals) {
                Sentence notLiteral = stringHandler.getConnectedSentence(ConnectiveName.NOT, literal);

                if (negation == null) {
                    negation = notLiteral;
                }
                else {
                    negation = stringHandler.getConnectedSentence(negation, ConnectiveName.AND, notLiteral);
                }
            }
        }

        if (negLiterals != null) {
            for (Literal literal : negLiterals) {
                if (negation == null) {
                    negation = literal;
                }
                else {
                    negation = stringHandler.getConnectedSentence(negation, ConnectiveName.AND, literal);
                }
            }
        }

        return negation;
	}

    @Override
    protected List<Clause> convertToListOfClauses() {
        List<Clause> list =  new ArrayList<>(1);
        list.add(this);
        return list;
    }


    @Override
	protected Clause moveNegationInwards() {
		return this; // Cannot go in any further.
	}
    @Override
	protected Clause skolemize(List<Variable> outerUniversalVars) {
		return this; // Cannot go in any further.
	}
    @Override
	protected Sentence distributeDisjunctionOverConjunction() {
		return this; // Cannot go in any further.
	}

    @Override
    protected Sentence distributeConjunctionOverDisjunction() {
        return this;
    }


    @Override
	public String toPrettyString(String lineStarter, int precedenceOfCaller, BindingList bindingList) { // Allow the 'lineStarter' to be passed in, e.g., the caller might want this to be quoted text.
		boolean useStdLogicNotation = stringHandler.printUsingStdLogicNotation();
		StringBuilder result     = new StringBuilder(returnWeightString());
		String  extra      = (extraLabel == null ? "" : " " +  extraLabel + " ");
		boolean firstOne   = true;
		int     counter    = 0;
		int     counter2   = 0;
		int     numPosLits = Utils.getSizeSafely(posLiterals);
		int     numNegLits = Utils.getSizeSafely(negLiterals);
		int     precedence = stringHandler.getConnectivePrecedence(stringHandler.getConnectiveName("=>"));
		int currentMaxLiteralsToPrint = (AllOfFOPC.truncateStrings ? maxLiteralsToPrint : 1000000); // Still use a huge limit just in case there is an infinite loop/
		
		if (numPosLits == 0 && numNegLits == 0) { return result + "true"  + extra; }

		if (numPosLits == 1 && numNegLits == 0) {
			return result       + posLiterals.get(0).toString(precedence, bindingList) + extra;
		}
		if (numPosLits == 0 && numNegLits == 1) {
			return result + "~" + negLiterals.get(0).toString(precedence, bindingList) + extra;
		}
		if (numPosLits == 0) { // In this case, write out the negative literals as a negated conjunction. I.e., 'p,q->false' is the same as '~p v ~q v false' which is the same as '~(p ^ q)'.
			result.append("~(");
            if (negLiterals != null) for (Literal literal : negLiterals) {
				if (counter2++ > currentMaxLiteralsToPrint) { result.append(" ... [plus ").append(Utils.comma(Utils.getSizeSafely(negLiterals) - currentMaxLiteralsToPrint)).append(" more negative literals]"); break; }
				if (firstOne) { firstOne = false; } else {
					result.append(" ^ "); }
				result.append(literal.toString(precedence, bindingList));
			}
			return result + ")" + extra;
		}

		if (precedenceOfCaller < precedence) { result.append("("); }
		if (useStdLogicNotation) {
			if (numNegLits > 0) {
				for (Literal literal : negLiterals) {
					if (counter2++ > currentMaxLiteralsToPrint) { result.append(" ... [plus ").append(Utils.comma(Utils.getSizeSafely(negLiterals) - currentMaxLiteralsToPrint)).append(" more negative literals]"); break; }
					if (firstOne) { firstOne = false; }
					else {
						if (++counter % stringHandler.numberOfLiteralsPerRowInPrintouts == 0) { result.append(" ^\n").append(lineStarter); }
						else { result.append(" ^ "); }
					}
					result.append(literal.toString(precedence, bindingList));
				}
				result.append(" => ");
				if (stringHandler.numberOfLiteralsPerRowInPrintouts > 0 && numNegLits >= stringHandler.numberOfLiteralsPerRowInPrintouts) { result.append("\n").append(lineStarter); }
			}
			counter = 0;
			if (numPosLits > 0) {
				firstOne = true;
				counter2 = 0;
				for (Literal literal : posLiterals) {
					if (counter2++ > currentMaxLiteralsToPrint) { result.append(" ... [plus ").append(Utils.comma(Utils.getSizeSafely(posLiterals) - currentMaxLiteralsToPrint)).append(" more positive literals]"); break; }
					if (firstOne) { firstOne = false; }
					else {
						if (++counter % stringHandler.numberOfLiteralsPerRowInPrintouts == 0) { result.append(" v\n").append(lineStarter); }
						else { result.append(" v "); } // The POSITIVE literals didn't have deMorgan's law applied to them since they weren't negated:  '(P^Q)->(RvS)' becomes '~(P^Q) v R v S' which becomes '~P v ~Q v R v S'.
					}
					result.append(literal.toString(precedence, bindingList));
				}
			}
			else { Utils.error("Should not reach here (by construction)."); }
		}
		else {
			if (numPosLits > 0) {
				for (Literal literal : posLiterals) {
					if (counter2++ > currentMaxLiteralsToPrint) { result.append(" ... [plus ").append(Utils.comma(Utils.getSizeSafely(posLiterals) - currentMaxLiteralsToPrint)).append(" more positive literals]"); break; }
					if (firstOne) { firstOne = false; }
					else {
						if (++counter % stringHandler.numberOfLiteralsPerRowInPrintouts == 0) { result.append(",\n").append(lineStarter); }
						else { result.append(", "); }
					}
					result.append(literal.toString(precedence, bindingList));
				}
                if ( numNegLits > 0 ) {
                    result.append(" :- ").append(extra);
                }
				if (stringHandler.numberOfLiteralsPerRowInPrintouts > 0 && numNegLits >= stringHandler.numberOfLiteralsPerRowInPrintouts) { 
					result.append("\n").append(lineStarter);
				}
			}
			else { Utils.error("Should not reach here (by construction)."); }
			counter = 0;
			if (numNegLits > 0) {
				firstOne = true;
				counter2 = 0;
				for (Literal literal : negLiterals) {
					if (counter2++ > currentMaxLiteralsToPrint) { result.append(" ... [plus ").append(Utils.comma(Utils.getSizeSafely(negLiterals) - currentMaxLiteralsToPrint)).append(" more negative literals]"); break; }

					if (firstOne) { firstOne = false; }
					else {
						if (++counter % stringHandler.numberOfLiteralsPerRowInPrintouts == 0) { result.append(",\n").append(lineStarter); }
						else { result.append(", "); }
					}
					result.append(literal.toString(precedence, bindingList));
				}
            }
		}
		if (precedenceOfCaller < precedence) { result.append(")"); }
		return result.toString();
	}

    public String toPrettyString(String lineStarter, int precedenceOfCaller, int literalsPerRow, BindingList bindingList) {
		int temp = stringHandler.numberOfLiteralsPerRowInPrintouts;
		stringHandler.numberOfLiteralsPerRowInPrintouts = literalsPerRow;
		String result = toPrettyString(lineStarter, precedenceOfCaller, bindingList);
		stringHandler.numberOfLiteralsPerRowInPrintouts = temp;
        return result;
	}

    @Override
        protected String toString(int precedenceOfCaller, BindingList bindingList) {
		if (stringHandler.prettyPrintClauses) {
			return toPrettyString("", precedenceOfCaller, 10, bindingList);
		}

		// If we want these to print variables like in Yap, it will take some thought.
		// Could add a flag ("printMeAsIs") and when this is false, create a NEW literal, set printMeAsIs=true on it, call stringHandler.renameAllVariables(), and then toString(precedenceOfCaller) on the result ...
		
		StringBuilder result = new StringBuilder(returnWeightString() + (AllOfFOPC.printUsingAlchemyNotation ? "" : "{ "));
		boolean firstOne = true;
		int currentMaxLiteralsToPrint = (AllOfFOPC.truncateStrings ? maxLiteralsToPrint : 1000000); // Still use a huge limit just in case there is an infinite loop/
		
		int counter = 0;
		if (posLiterals != null) for (Literal literal : posLiterals) {
			if (counter++ > currentMaxLiteralsToPrint) { result.append(" ... [plus ").append(Utils.comma(Utils.getSizeSafely(posLiterals) - currentMaxLiteralsToPrint)).append(" more positive literals]"); break; }

			if (firstOne) { firstOne = false; } else {
				result.append(" v "); }
			result.append(literal.toString(precedenceOfCaller, bindingList));
		}
		counter = 0;
		if (negLiterals != null) for (Literal literal : negLiterals) {
			if (counter++ > currentMaxLiteralsToPrint) { result.append(" ... [plus ").append(Utils.comma(Utils.getSizeSafely(negLiterals) - currentMaxLiteralsToPrint)).append(" more negative literals]"); break; }
			if (firstOne) { firstOne = false; } else {
				result.append(" v "); }
			result.append(AllOfFOPC.printUsingAlchemyNotation ? "!" : "~").append(literal.toString(precedenceOfCaller, bindingList)); // NOTE: due to '!' WILL cannot read Alchemy files.  TODO fix.
		}
		return result + (AllOfFOPC.printUsingAlchemyNotation ? "" : " }") + (extraLabel == null ? "" : " /* " +  extraLabel + " */");

	}

    public Boolean getBodyContainsCut() {
        if ( bodyContainsCut == null ) checkForCut();

        return bodyContainsCut;
    }

    /* Set the bodyContainsCut parameter.
     *
     * <rant>
     * This really shouldn't be set directly, but since the
     * posLiterals and negLiterals are exposed (to the whole damn
     * world, ugg!) we have no choice but to handle this directly.
     * This is an extremely good example of something that for some
     * reason is being calculated on the outside, but should really
     * just be done internally as a side-effect to the getter and
     * setter code (or in this case the add/remove pos/neg literal
     * code that should exist but doesn't).
     * </rant>
     *
     * @param bodyContainsCut the bodyContainsCut to set
     */
    public void setBodyContainsCut(Boolean bodyContainsCut) {
        this.bodyContainsCut = bodyContainsCut;
    }

    public Clause getNegatedQueryClause() throws IllegalArgumentException {

        Clause result;

        if ( getPosLiteralCount() == 0 ) {
            result = this;
        }
        else if ( getNegLiteralCount() == 0 ) {
            // If we have only positive literals, just flip things around.
            // This would be convenient, but I am not sure what it would
            // break, so I will comment it out for now.
            result = stringHandler.getClause(null, posLiterals);
            result.extraLabel = extraLabel;
        }
        else {
            throw new IllegalArgumentException("Clause could not be converted to legal SLDQuery clause: " + this + ".");
        }
        return result;
    }

    @Override
    public <Return,Data> Return accept(SentenceVisitor<Return,Data> visitor, Data data) {
        return visitor.visitClause(this, data);
    }
	@Override
	   public int countVarOccurrencesInFOPC(Variable v) {
        int total = 0;
        if (posLiterals != null) {
            for (Literal litP : posLiterals) {
                total += litP.countVarOccurrencesInFOPC(v);
            }
        }
        if (negLiterals != null) {
            for (Literal litN : negLiterals) {
                total += litN.countVarOccurrencesInFOPC(v);
            }
        }
        return total;
    }
    
}
