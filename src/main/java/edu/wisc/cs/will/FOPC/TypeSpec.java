package edu.wisc.cs.will.FOPC;

import edu.wisc.cs.will.Utils.Utils;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * @author shavlik
 * The material in this class is used in ILP and MLNs, though it can play a role in other logical-reasoning systems.
 */
public class TypeSpec extends AllOfFOPC implements Serializable, Cloneable { // IMPORTANT NOTE: if adding more symbols here, also edit atTypeSpec() in the parser.

    private final static int unspecifiedMode = -1; // For use when modes aren't needed.
	private final static int modeNotYetSet = 0; // Mark that this mode will be set later, when more information is available.
	final static int plusMode      = 1; // An 'input' argument (should be bound when the predicate or function containing this is called).
	private final static int onceMode      = 2; // An 'input' argument that appears exactly ONCE in the clause SO FAR (can be reused later).
	public final static int minusMode     = 3; // An 'output' argument - need not be bound.
	private final static int novelMode     = 4; // An 'output' argument that is a NEW variable.
	private final static int constantMode  = 5; // An argument that should be a constant (i.e., not a variable).
	private final static int thisValueMode = 6; // This SPECIFIC constant should fill this argument slot.
	private final static int equalMode     = 7; // This variable must also appear in the body of a clause for that clause to be acceptable (otherwise, same as '+').
	private final static int minusOrConstantMode =  8; // Means BOTH '-' and '#'.
	private final static int plusOrConstantMode  =  9; // Means BOTH '+' and '#'.
	private final static int novelOrConstantMode = 10; // Means BOTH '^' and '#' (currently this one has no single-character name
	private final static int starMode            = 11; // Look up the mode in the stringHandler.
	private final static int notHeadVarMode  	= 12; // The variable shouldn't be in the head of the clause.

    public Integer mode;    // One of the above, which are used to describe how this argument is to be used.
	public Type    isaType; // Can be "human," "book," etc.  Type hierarchies are user-provided.
	transient HandleFOPCstrings stringHandler;

	public TypeSpec(char modeAsChar, String typeAsString, HandleFOPCstrings stringHandler) {
		this.stringHandler = stringHandler;
		if      (modeAsChar == '+') { mode = plusMode;      } // If additions to this, be sure to add to isaModeSpec().
		else if (modeAsChar == '$') { mode = onceMode;      }
		else if (modeAsChar == '-') { mode = minusMode;     }
		else if (modeAsChar == '^') { mode = novelMode;     }
		else if (modeAsChar == '#') { mode = constantMode;  }
		else if (modeAsChar == '@') { mode = thisValueMode; }
		else if (modeAsChar == '*') { mode = starMode;      }
		else if (modeAsChar == '=') { mode = equalMode;     }
		else if (modeAsChar == '&') { mode = minusOrConstantMode; }
		else if (modeAsChar == ':') { mode = plusOrConstantMode;  }
		else if (modeAsChar == '`') { mode = notHeadVarMode;  }
		else if (modeAsChar == '>') { mode = modeNotYetSet;  }
		// novelOrConstantMode
		else if (modeAsChar == ' ') { mode = unspecifiedMode;     }
		else { Utils.error("Unknown mode character: '" + modeAsChar + "'"); }
		isaType = stringHandler.isaHandler.getIsaType(typeAsString);
	}	
	public static boolean isaModeSpec(char c) { // Also look at FileParser.processTerm
		return c == '+' || c == '$' || c == '-' || c == '^' || c == '#' || c == '@' || c == '*' || c == '=' || c == '&' || c == ':' || c == '>'|| c == '`';
	}
	public TypeSpec(Type isaType, HandleFOPCstrings stringHandler) {
		this.stringHandler = stringHandler;
		this.mode          = unspecifiedMode;
		this.isaType       = isaType;
	}

	/*
         * Collect those type specifications that are for INPUT arguments. For
         * the other arguments, use 'null' (this way two different
         * specifications such as '(+human,-human,+dog)' and
         * '(-human,+human,-dog)' will be differentiated).
         *
         * @return A list of the argument types for the given predicate specification.
         */
	public static List<Type> getListOfInputArgumentTypes(PredicateSpec fullTypeSpec) {
		List<Type> inputArgumentTypes = new ArrayList<>(1);
		for (TypeSpec spec : fullTypeSpec.getTypeSpecList()) {
			if (spec.mustBeBound()) { inputArgumentTypes.add(spec.isaType); } else { inputArgumentTypes.add(null); }
		}
		return inputArgumentTypes;
	}	
	
    @Override
	public int hashCode() { // Need to have equal objects produce the same hash code.
		final int prime = 31;
		int result = 1;
		result = prime * result + mode;
		result = prime * result + ((isaType == null) ? 0 : isaType.hashCode());
		return result;
	}	
    @Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TypeSpec)) { return false; }
		TypeSpec typeSpec = (TypeSpec) obj;
		return (mode.equals(typeSpec.mode) && isaType == typeSpec.isaType);
	}

	public boolean mustBeBound() {
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == plusMode || modeToUse == equalMode || modeToUse == onceMode;
	}

	public boolean mustBeBoundAndAppearOnlyOnce() {
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == onceMode;
	}
	
	public boolean canBeNewVariable() {
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == minusMode || modeToUse == minusOrConstantMode || mustBeNewVariable();
	}
	
	public boolean mustBeNewVariable() {
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == novelMode || modeToUse == novelOrConstantMode; // This might be buggy - it might not allow Constant to be used?  Depends on how the inner loop's child-generator handles this.
	}
	
	public boolean mustBeThisValue()	{
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == thisValueMode;
	}
	
	public boolean mustBeConstant()	{
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == constantMode;
	}
	
	
	public boolean mustNotBeHeadVar()	{
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == notHeadVarMode;
	}
	
	public boolean canBeConstant()	{
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == constantMode || modeToUse == minusOrConstantMode || modeToUse == plusOrConstantMode || modeToUse == novelOrConstantMode;
	}
	
	public boolean mustBeInBody()	{
		int modeToUse = mode;
		if (mode == starMode) { modeToUse = stringHandler.getStarMode(); }
		return modeToUse == equalMode;
	}
	
	String getModeString() {
		return getModeString(mode);
	}
	private static String getModeString(int modeToUse) {
		switch (modeToUse) {
			case plusMode:      return "+";
			case onceMode:      return "$";
			case minusMode:     return "-";
			case novelMode:     return "^";
			case constantMode:  return "#";
			case thisValueMode: return "@";
			case equalMode:     return "=";
			case starMode:      return "*";
			case minusOrConstantMode: return "&";
			case plusOrConstantMode:  return "%";
			case notHeadVarMode:  	  return "`";
			case novelOrConstantMode: return "novelConst";
			case unspecifiedMode:     return "";
			case modeNotYetSet:       return ">";
			default: Utils.error("Unknown mode type code: '" + modeToUse + "'");
					 return null;
		}		
	}

	@Override
	public String toPrettyString(String newLineStarter, int precedenceOfCaller, BindingList bindingList) {
		return getModeString() + isaType;
	}
    @Override
	public String toString(int precedenceOfCaller, BindingList bindingList) {
		return getModeString() + isaType;
	}
	@Override
	public TypeSpec applyTheta(Map<Variable, Term> bindings) {
		return this;
	}

    public Type getType() {
        return isaType;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        if (!(in instanceof FOPCInputStream)) {
            throw new IllegalArgumentException(getClass().getCanonicalName() + ".readObject() input stream must support FOPCObjectInputStream interface");
        }

        in.defaultReadObject();

        FOPCInputStream fOPCInputStream = (FOPCInputStream) in;

        this.stringHandler = fOPCInputStream.getStringHandler();
    }
    
	@Override
	public int countVarOccurrencesInFOPC(Variable v) {
		return 0;
	}

	public TypeSpec copy() {
		return clone();
	}

    protected TypeSpec clone() {
        try {
            return (TypeSpec) super.clone();
        } catch (CloneNotSupportedException ex) {
            return null;
        }
    }
}
