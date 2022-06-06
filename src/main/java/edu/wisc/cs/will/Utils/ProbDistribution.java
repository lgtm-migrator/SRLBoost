package edu.wisc.cs.will.Utils;

import edu.wisc.cs.will.Boosting.Utils.BoostingUtils;

import java.util.Arrays;

/*
 * @author tkhot
 */
public class ProbDistribution {

	/* Used if we don't have a distribution over multiple values but a single probability */
	private double probOfBeingTrue;
	
	private double[] probDistribution = null;
	
	private boolean hasDistribution;
	
	public ProbDistribution(double prob) {
		setProbOfBeingTrue(prob);
	}

	public ProbDistribution(double prob, boolean regression) {
		setProbOfBeingTrue(prob, regression);
	}

	public void setProbOfBeingTrue(double probOfBeingTrue, boolean regression) {
		if (regression) {
			setHasDistribution(false);
			this.probOfBeingTrue = probOfBeingTrue;
		}
	}

	public ProbDistribution(ProbDistribution copy) {
		this.hasDistribution  = copy.hasDistribution;
		if (hasDistribution) {
			this.probDistribution = copy.probDistribution.clone();
		} else {
			this.probOfBeingTrue = copy.probOfBeingTrue;
		}
	}
	
	/*
	 * Construct distribution using sigmoid
	 */
	public ProbDistribution(RegressionValueOrVector reg) {
		this(reg, true);
	}
	
	public ProbDistribution(RegressionValueOrVector reg, boolean useSigmoid) {
		if (useSigmoid) {
			initUsingSigmoid(reg);
		} else {
			initAfterNormalizing(reg); 
		}
	}
	
	private void initAfterNormalizing(RegressionValueOrVector reg) {
		if (reg.isHasVector()) {
			double deno = VectorStatistics.sum(reg.getRegressionVector());
			double[] probDist = VectorStatistics.scalarProduct(reg.getRegressionVector(), 1/deno);
			setProbDistribution(probDist);
		} else {
			setProbOfBeingTrue(reg.getSingleRegressionValue());
		}
	}

	private void initUsingSigmoid(RegressionValueOrVector reg) {
		if (reg.isHasVector()) {
			double[] exp = VectorStatistics.exponentiate(reg.getRegressionVector());
			double deno = VectorStatistics.sum(exp);
			double[] probDist = VectorStatistics.scalarProduct(exp, 1/deno);
			for (int i = 0; i < probDist.length; i++) {
				if (Double.isNaN(probDist[i])) {
					probDist[i] = 1;
				}
			}
			setProbDistribution(probDist);
		} else {
			setProbOfBeingTrue(BoostingUtils.sigmoid(reg.getSingleRegressionValue(), 0));
		}
	}
	public void scaleDistribution(double scalar) {
		if (isHasDistribution()) {
			probDistribution = VectorStatistics.scalarProduct(probDistribution, scalar); 
		} else {
			probOfBeingTrue *= scalar;
		}
	}
	
	public void addDistribution(ProbDistribution add){
		// If null, then add 0
		if (add == null) {
			return;
		}
		if (isHasDistribution()) {
			probDistribution = VectorStatistics.addVectors(this.probDistribution, add.probDistribution);
		} else {
			probOfBeingTrue += add.probOfBeingTrue;
		}
	}

	@Override
	public String toString() {
		if (isHasDistribution()) {
			return Arrays.toString(probDistribution);
		} else{
			return probOfBeingTrue+"";
		}
	}

	public double getProbOfBeingTrue() {
		if (isHasDistribution()) {
			Utils.error("Expected single probability value but contains distribution");
		}
		return probOfBeingTrue;
	}

	private void setProbOfBeingTrue(double probOfBeingTrue) {
		if (probOfBeingTrue > 1) {
			Utils.error("Probability greater than 1!!: " +  probOfBeingTrue);
		}
		setHasDistribution(false);		
		this.probOfBeingTrue = probOfBeingTrue;
	}

	public double[] getProbDistribution() {
		if (!isHasDistribution()) {
			Utils.error("Expected distribution but contains single probability value");
		}
		return probDistribution;
	}

	private void setProbDistribution(double[] probDistribution) {
		setHasDistribution(true);
		this.probDistribution = probDistribution;
	}

	public boolean isHasDistribution() {
		return hasDistribution;
	}

	private void setHasDistribution(boolean hasDistribution) {
		this.hasDistribution = hasDistribution;
	}

	public double norm() {
		if (isHasDistribution()) {
			return Math.sqrt(VectorStatistics.dotProduct(probDistribution, probDistribution));
		}
		return probOfBeingTrue;
	}

	/*
	 * Return a randomly selected value from the distribution.
	 */
	public int randomlySelect() {
		if (!isHasDistribution()) {
			return (Utils.random() < probOfBeingTrue) ? 1 : 0;
		}
		double cumulative = 0;
		double rand = Utils.random();
		for (int i = 0; i < probDistribution.length; i++) {
			cumulative += probDistribution[i];
			if (rand < cumulative) {
				return i;
			}
		}
		Utils.error("Cumulative distribution doesn't sum to 1. Sum:" + cumulative);
		return 0;
	}

}
