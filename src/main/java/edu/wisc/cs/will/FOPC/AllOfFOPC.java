package edu.wisc.cs.will.FOPC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author shavlik
 */
public abstract class AllOfFOPC {
	protected final static int debugLevel = 0;   // Used to control output from this project (0 = no output, 1=some, 2=much, 3=all).
	final static int defaultPrecedence = Integer.MIN_VALUE;  // This plays it safe and uses a lot of parentheses.
	public          static boolean renameVariablesWhenPrinting = false;
	public          static boolean truncateStrings             = true; // Prevent printing very long strings if true.
	public          static boolean printUsingAlchemyNotation   = false;
 
    /**
	 * This class is a superclass of all FOPC constructs.
	 */
	public AllOfFOPC() {
	}
	
	static List<AllOfFOPC> makeList(AllOfFOPC item) {
		List<AllOfFOPC> result = new ArrayList<>(1);
		result.add(item);
		return result;
	}	
	static List<AllOfFOPC> makeList(AllOfFOPC item, List<AllOfFOPC> rest) {
		List<AllOfFOPC> result = new ArrayList<>(1 + rest.size());
		result.add(item);
		result.addAll(rest); // Do this safely so no shared lists.
		return result;
	}
	public abstract AllOfFOPC applyTheta(Map<Variable,Term> bindings);
	public abstract int       countVarOccurrencesInFOPC(Variable v);

    public abstract String    toString(                             int precedenceOfCaller, BindingList bindingList);
	public abstract String    toPrettyString(String newLineStarter, int precedenceOfCaller, BindingList bindingList);
	  
	public String toString(int precedence, List<Term> items) {
		StringBuilder result = new StringBuilder();
		boolean firstTime = true;
		for(Term t : items) {
			if (firstTime) { firstTime = false; } else { result.append(", "); }
			result.append(t.toString(precedence));
		}
		return result.toString();
	}
	public String toPrettyString() {
		return toPrettyString("", defaultPrecedence); // Use some average value?
	}
	public String toPrettyString(String newLineStarter) {
		return toPrettyString(newLineStarter, defaultPrecedence); // Use some average value?
	}
    @Override
	public String toString() {
		return toString(defaultPrecedence); // Use some average value?
	}

    public String toString(BindingList bindingList) {
        return toString(defaultPrecedence, bindingList);
    }

    public String toString(int precedenceOfCaller) {
        if ( renameVariablesWhenPrinting ) {
            return toString(precedenceOfCaller, new BindingList());
        }
		return toString(precedenceOfCaller, (BindingList)null);
    }

    public String toPrettyString(String newLineStarter, int precedenceOfCaller) {
        if ( renameVariablesWhenPrinting ) {
            return toPrettyString(newLineStarter, precedenceOfCaller, new BindingList());
        }
		return toPrettyString(newLineStarter, precedenceOfCaller, null);
    }
}
