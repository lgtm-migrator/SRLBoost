package edu.wisc.cs.will.Boosting.RDN;

import edu.wisc.cs.will.Boosting.Common.SRLInference;
import edu.wisc.cs.will.Boosting.MLN.MLNInference;
import edu.wisc.cs.will.Boosting.RDN.Models.DependencyNetwork;
import edu.wisc.cs.will.Boosting.RDN.Models.DependencyPredicateNode;
import edu.wisc.cs.will.Boosting.RDN.Models.DependencyPredicateNode.PredicateType;
import edu.wisc.cs.will.Boosting.RDN.Models.RelationalDependencyNetwork;
import edu.wisc.cs.will.DataSetUtils.Example;
import edu.wisc.cs.will.FOPC.PredicateName;
import edu.wisc.cs.will.Utils.ProbDistribution;
import edu.wisc.cs.will.Utils.RegressionValueOrVector;
import edu.wisc.cs.will.Utils.Utils;
import edu.wisc.cs.will.Utils.condor.CondorFileWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/*
 * The class sets the probability of examples, given a joint RDN model (i.e. RDN model
 * for each predicate). 
 * 
 * @author Tushar Khot
 *
 */
public class JointModelSampler extends SRLInference {

	private final boolean useMLNInference;

	/*
	 * 
	 * @param model - The joint model to use
	 * @param setup - The WILLSetup class with the facts
	 * @param cmdArgs - Arguments set by user.
	 */
	JointModelSampler(JointRDNModel model, WILLSetup setup) {
		this(model, setup, false);
	}

	JointModelSampler(JointRDNModel model, WILLSetup setup, boolean useMLNs) {
		super(setup);
		this.jointModel   = model;
		this.setup   = setup;
		this.useMLNInference = useMLNs;
		// Use the model to obtain the RDN structure
		rdn = new RelationalDependencyNetwork(model, setup);
	}

	/*
	 * This method computes the marginal probabilities of {@link RegressionRDNExample} by setting the 
	 * probOfExample in each example. Make sure to pass all possible groundings of the target predicates
	 * as it would be using Gibbs Sampling over these examples. If there is no recursion or dependencies
	 * between the target predicates, Gibbs sampling wont be used and it is safe to pass only some examples. 
	 * @param jointExamples - The set of examples for which we want to compute the probabilities
	 */
	public void getMarginalProbabilities(Map<String, List<RegressionRDNExample>> jointExamples) {
		Utils.println("\n% Starting getMarginalProbabilities.");
		sampleWorldStates(jointExamples);
	}
	
	private void sampleWorldStates(Map<String, List<RegressionRDNExample>> originalJointExamples) {
		// TODO(@hayesall): This appears to be the only method which uses `printNetwork`

		// Get the order of the predicates for Gibbs sampling.
		// We want to order predicates by the number of query/target predicate parents.
		// For e.g., if "sameBib" has two query predicates as parents, while "sameTitle" has only one
		// query predicate as parent, we should sample in the order {sameTitle, sameBib}.
		Collection<String> orderedPredicates = getOrderedPredicates(originalJointExamples.keySet());
		// Print after getting the order, as we print the order in the DOT file too.
		String rdnDotFile = setup.cmdArgs.getModelDirVal() + "bRDNs/dotFiles/rdn.dot";
		printNetwork(rdn, rdnDotFile);

		// Making a copy of the original map, since we will update the map to handle multi-class examples. 
		// Only the map is copied, the examples are still the same. So careful while modifying the actual examples in jointExamples
		Map<String, List<RegressionRDNExample>> jointExamples = new HashMap<>(originalJointExamples);
		for (String target : originalJointExamples.keySet()) {
			List<RegressionRDNExample> examples = originalJointExamples.get(target);
			MultiClassExampleHandler mcExHandler= setup.getMulticlassHandler();
			// Update examples if multi-class predicate
			if (mcExHandler.isMultiClassPredicate(target)) {

				List<RegressionRDNExample> newMulticlassExamples = new ArrayList<>();
				Set<String> seenExamples = new HashSet<>();
				for (RegressionRDNExample rex : examples) {
					RegressionRDNExample newRex;
					if (rex.predicateName.name.startsWith(WILLSetup.multiclassPredPrefix)) {
						// Already a multi-class example
						newRex = rex;
					} else {
						newRex = mcExHandler.morphExample(rex);
					}
					String newRexStr = newRex.toString();
					if (!seenExamples.contains(newRexStr)) {
						seenExamples.add(newRexStr);
						newMulticlassExamples.add(newRex);
					} 
					// }
				}
				jointExamples.put(target, newMulticlassExamples);
			}
		}

		// Break into components for MAP inference
		sampleExampleProbabilities(jointExamples, orderedPredicates);

		// Remove all examples.
		removeAllExamples(originalJointExamples);

		updateProbabilitiesForInput(originalJointExamples, jointExamples);

	}

	private void sampleExampleProbabilities(
			Map<String, List<RegressionRDNExample>> jointExamples,
			Collection<String> orderedPredicates) {
		
		boolean needSampling = false;

		Map<String,List<Example>>  posEgs = new HashMap<>();
		Map<String,List<Example>>  negEgs = new HashMap<>();

		// First init the pos and neg examples. The posEgs and negEgs are updated with
		// each sample and become facts for the next round and hence need to be collected.
		Map<String, List<double[]>> counters = new HashMap<>();

		for (String target : jointExamples.keySet()) {
			List<RegressionRDNExample> examples = jointExamples.get(target);

			posEgs.put(target, new ArrayList<>());
			negEgs.put(target, new ArrayList<>());
			if (examples.isEmpty()) {
				continue;
			}
			// Based on the current sampled state(which would have been randomly assigned in the 
			// RegressionRDNExample constructor), find out the current positive and negative examples.
			updatePosNegWithSample(target, examples, posEgs, negEgs);
			counters.put(target, new ArrayList<>());
			// Find out what predicates are precomputed and are needed for predicting target predicates.
			Collection<PredicateName> pnames = rdn.getAncestorsOfType(target, PredicateType.COMPUTED);
		}

		String last_target = null;
		for (String target : orderedPredicates) {
			// No examples for this predicate
			if (!jointExamples.containsKey(target)) {
				continue;
			}
			ConditionalModelPerPredicate mod = jointModel.get(target);
			/*
			 * If this query predicate doesn't have any query predicates as parents,
			 * we can just compute the probabilities based on evidence and hence we 
			 * don't need sampling.
			 * Also if there is only one query predicate, we don't need joint inference.
			 */
			if (hasNoTargetParents(target) || jointExamples.size() == 1) {
				List<RegressionRDNExample> examples = jointExamples.get(target);

				if (examples.isEmpty()) {
					continue;
				}
				setup.prepareFactsAndExamples(posEgs, negEgs, target, false, null);
				SRLInference sampler;

				if (useMLNInference) {
					sampler = new MLNInference(setup, jointModel);
				} else {
					sampler = new SingleModelSampler(mod, setup, jointModel);
				}

				/*
				 * If we are using recursion for this target, tell SingleModelSampler to use Gibbs sampling 
				 * for probabilities.
				 */
				sampler.getProbabilities(examples);
			} else {
				// If there is one query predicate that has query predicate as parents, we need sampling.
				needSampling = true;
				Utils.println("Need sampling for:" + target);
			}
		}
		if (!needSampling) {
			Utils.println("% No Gibbs sampling needed during inference.");
			return;
		}	

		// We should atleast sample 10X number of states considering there will be 2^examples number of states.
		// But don't want to exceed 1000
		// Now do Gibbs sampling.
		int numOfSamples = 1000;
		int burnInSteps = 200;
		for (int i = -burnInSteps; i < numOfSamples; i++) {
			// For all query predicates.
			for (String target : jointExamples.keySet()) {
				ConditionalModelPerPredicate mod = jointModel.get(target);
				List<RegressionRDNExample> examples = jointExamples.get(target);
				// Double negation here. If "target" has a query predicate as parent,
				// then we need to compute the probabilities and generate a sample.
				if(!hasNoTargetParents(target)) {
					setup.prepareFactsAndExamples(posEgs, negEgs,
							target, false, last_target);

					if (useMLNInference) {
						MLNInference sampler = new MLNInference(setup, jointModel);
						sampler.getProbabilities(examples);
						getSampleForExamples(examples);
					} else {
						// Assume that the model doesn't have recursion as the "if" condition
						// takes care of how to sample the examples.
						SingleModelSampler sampler = new SingleModelSampler(mod,setup, jointModel);

						sampler.getProbabilities(examples);
						getSampleForExamples(examples);
					}
				} else {
					// Doesn't have a query predicate as parent, so probability doesn't change,
					// in every iteration. But we still need a sample as others may depend on it.
					getSampleForExamples(examples);
				}
				// Use the sample
				updatePosNegWithSample(target, examples, posEgs, negEgs);
				last_target = target;
			}
			if (i % 100 == 0 && i != 0) {
				Utils.println("%   Sample #" + i);
			}
			// Get counts for the sample, after burn-in
			if (i >= 0) {
				getCountsForSample(jointExamples, counters);
			}
		}
		// Set the probability.
		setProbabilityFromCounters(jointExamples, counters);
	}

	/*
	 * Orders predicates by the number of query predicates that are ancestors of 
	 * a predicate. Actually, it marks the best predicate; finds the next best predicate
	 * with least number of unmarked predicates and marks it. This is done till all
	 * predicates are marked and the order of marking is the order of predicates.
	 * @param keySet - The set of predicates to be ordered
	 * @return A list of ordered predicates.
	 */
	private Collection<String> getOrderedPredicates(Set<String> keySet) {
		// If only one target, no order.
		if (keySet.size() == 1) {
			return keySet;
		}
		// Result stored here
		ArrayList<String> orderedPreds = new ArrayList<>();
		// Temporary array which is emptied 
		ArrayList<String> newSet = new ArrayList<>(keySet);
		while(!newSet.isEmpty()) {
			String bestPredicate= "";
			int leastNeighbours = newSet.size() + 1;
			for (String predicate : newSet) {
				int nbrs = getNumUnmarkedParents(predicate);
				if (nbrs < leastNeighbours) {
					leastNeighbours = nbrs;
					bestPredicate = predicate;
				}
			}

			// Add best predicate
			orderedPreds.add(bestPredicate);
			newSet.remove(bestPredicate);
			markBestPredicate(bestPredicate, orderedPreds.size());
		}
		// For now just return the same set
		return orderedPreds;
	}


	/**
	 * Used by getOrderedPredicates, to mark a predicate with a number
	 */
	private void markBestPredicate(String bestPredicate, int number) {
		DependencyPredicateNode node = rdn.getDependencyNode(setup.getHandler().getPredicateName(bestPredicate));
		node.setOrder(number);
	}

	/**
	 * Number of ancestors that are unmarked.
	 */
	private int getNumUnmarkedParents(String predicate) {
		Collection<PredicateName> ancestors = rdn.getQueryParents(predicate);
		int count = 0;
		for (PredicateName predicateName : ancestors) {
			DependencyPredicateNode node = rdn.getDependencyNode(predicateName);
			if (node.getOrder() < 0) {
				count++;
			}
		}
		return count;
	}

	private void updatePosNegWithSample(String target, List<RegressionRDNExample> examples,
										Map<String,List<Example>> posEgs, 
										Map<String,List<Example>> negEgs) {

		// All examples are passed in, so reset the example list.
		assert examples != null;

		if (examples.isEmpty()) {
			Utils.error("Expected non-null and non-empty example list");
		}

		posEgs.get(target).clear();
		negEgs.get(target).clear();

		List<Example> posList = posEgs.get(target);
		List<Example> negList = negEgs.get(target);

		int numberValues = setup.getMulticlassHandler().numConstantsForPredicate(target);

		for (RegressionRDNExample rex : examples) {
			if (!rex.predicateName.name.equals(target) && !rex.predicateName.name.equals(WILLSetup.multiclassPredPrefix + target)) {
				Utils.error("Found example: '" + rex + "'\nwhile sampling for " + target);
			}
			if (setup.getMulticlassHandler().isMultiClassPredicate(target)) {
				for (int i = 0; i < numberValues; i++) {
					Example ex = setup.getMulticlassHandler().createExampleFromClass(rex, i);
					if (rex.getSampledValue() == i) {
						posList.add(ex);
					} else {
						negList.add(ex);
					}
				}
			} else {
				if (rex.getSampledValue() == 1) {
					posList.add(rex);
				} else {
					negList.add(rex);
				}
			}
		}
	}

	/*
	 * Writes the RDN to a DOT file. Use GraphViz to convert it to an image.
	 */
	private void printNetwork(DependencyNetwork dn, String filename) {
		try {
			Utils.ensureDirExists(filename);
			// Create a new file.
			BufferedWriter writer = new BufferedWriter(new CondorFileWriter(filename, false));
			dn.printNetworkInDOT(writer);
			writer.close();
		} catch (IOException e) {
			Utils.reportStackTrace(e);
			Utils.error("Problem in printRDN.");
		}
	}

	private void updateProbabilitiesForInput(Map<String, List<RegressionRDNExample>> originalJointExamples,
											 Map<String, List<RegressionRDNExample>> jointExamples) {
		for (String target : jointExamples.keySet()) {
			int i = 0;
			// Store the example probabilities for the multi-class predicates.
			// These probabilities are used to fill the probabilities in the originalJointExamples
			Map<String, Double> exampleProbabilities = null;
			for (RegressionRDNExample rex : jointExamples.get(target)) {
				
				if (setup.getMulticlassHandler().isMultiClassPredicate(target)) {
					if (exampleProbabilities == null) { exampleProbabilities = new HashMap<>();}
					ProbDistribution distr = rex.getProbOfExample();
					for (int index = 0; index < distr.getProbDistribution().length; index++) {
						Example ex = setup.getMulticlassHandler().createExampleFromClass(rex, index);
						exampleProbabilities.put(ex.toString(), distr.getProbDistribution()[index]);
					}
				} else {
					originalJointExamples.get(target).get(i).setProbOfExample(rex.getProbOfExample());
				}
				i++;
			}
			
			// If the target was multi-class, we populated the probabilities for the individual examples
			// corresponding to each class 
			if (exampleProbabilities != null) {
				for (RegressionRDNExample rex : originalJointExamples.get(target)) {

					// Input was multi-class, no need to transfer probs
					if (rex.predicateName.name.startsWith(WILLSetup.multiclassPredPrefix)) { continue; }

					String rexstring = rex.toString();
					if (exampleProbabilities.containsKey(rexstring)) {
						double prob = exampleProbabilities.get(rexstring);
						ProbDistribution distr=new ProbDistribution(prob);
						rex.setProbOfExample(distr);
					} else {
						Utils.error("Unseen example during sampling:" + rexstring);
					}
				}
			}
		}
		
	}
	private void setProbabilityFromCounters(Map<String, List<RegressionRDNExample>> jointExamples,
											Map<String, List<double[]>> counters) {
		for (String target : jointExamples.keySet()) {
			int i=0;
			List<double[]> valueCounts = counters.get(target);

			for (RegressionRDNExample rex : jointExamples.get(target)) {
				double[] counts = valueCounts.get(i);
				
				if (setup.getMulticlassHandler().isMultiClassPredicate(target)) {
					ProbDistribution distr = new ProbDistribution(new RegressionValueOrVector(counts), false);
					rex.setProbOfExample(distr);
					
				} else {
					ProbDistribution distr = new ProbDistribution(counts[1]/(counts[1] + counts[0]));
					rex.setProbOfExample(distr);
				}
				i++;
			}
			
		}
	}

	private void removeAllExamples(Map<String, List<RegressionRDNExample>> jointExamples) {
		for (String target : jointExamples.keySet()) {
			for (RegressionRDNExample eg : jointExamples.get(target)) {
				setup.removeFact(eg);
			}
		}
	}

	private void getCountsForSample(
			Map<String,
			List<RegressionRDNExample>> jointExamples,
			Map<String, List<double[]>> counters) {
		for (String target : jointExamples.keySet()) {
			int i=0;
			int totalVals = setup.getMulticlassHandler().numConstantsForPredicate(target);
			for (RegressionRDNExample rex : jointExamples.get(target)) {
				List<double[]> valueCounts = counters.get(target);
				if (valueCounts.size() <= i) {
					valueCounts.add(new double[totalVals]);
				}
				int val = rex.getSampledValue();
				valueCounts.get(i)[val] += 1;
				i++;
			}
		}
	}
	
	private void getSampleForExamples(List<RegressionRDNExample> examples) {
		for (RegressionRDNExample rex : examples) {
			ProbDistribution prob = rex.getProbOfExample();
			int val = prob.randomlySelect();
			rex.setSampledValue(val);
		}
	}

	public void setMaxTrees(int j) {
		for (ConditionalModelPerPredicate model : this.jointModel.values()) {
			model.setNumTrees(j);
		}
	}

	@Override
	public ProbDistribution getExampleProbability(Example eg) {
		Utils.error("JointModelSampler can't return single example probability as it does sampling");
		return null;
	}
	
}
