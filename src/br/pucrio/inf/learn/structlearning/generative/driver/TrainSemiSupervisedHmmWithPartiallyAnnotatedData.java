package br.pucrio.inf.learn.structlearning.generative.driver;

import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import br.pucrio.inf.learn.structlearning.generative.core.HmmModel;
import br.pucrio.inf.learn.structlearning.generative.core.HmmTrainer;
import br.pucrio.inf.learn.structlearning.generative.core.SemiSupervisedHmmTrainer;
import br.pucrio.inf.learn.structlearning.generative.data.Corpus;
import br.pucrio.inf.learn.structlearning.generative.data.DatasetExample;

/**
 * Semi-supervised train an HMM model using two trainsets. The first one is used
 * as a fully-annotated dataset but the second one is used as a partially
 * annotated dataset, where 0-annotated tokens are treated as unannotated
 * examples.
 * 
 * @author eraldof
 * 
 */
public class TrainSemiSupervisedHmmWithPartiallyAnnotatedData {

	private static Log logger = LogFactory
			.getLog(TrainSemiSupervisedHmmWithPartiallyAnnotatedData.class);

	public static void main(String[] args) throws Exception {

		if (args.length != 6) {
			System.err.print("Syntax error. Correct syntax:\n"
					+ "	<trainfile> <observation_feature>"
					+ " <state_feature> <modelfile> <numiterations> <seed>\n");
			System.exit(1);
		}

		int arg = 0;
		String trainFileName = args[arg++];
		String observationFeatureLabel = args[arg++];
		String stateFeatureLabel = args[arg++];
		String modelFileName = args[arg++];
		int numIterations = Integer.parseInt(args[arg++]);
		int seed = Integer.parseInt(args[arg++]);

		System.out.println(String.format(
				"Unsupervised training HMM model with the following parameters:\n"
						+ "	Train file: %s\n" + "	Observation feature: %s\n"
						+ "	State feature: %s\n" + "	Model file: %s\n"
						+ "	# iterations: %d\n" + "	Seed: %d\n", trainFileName,
				observationFeatureLabel, stateFeatureLabel, modelFileName,
				numIterations, seed));

		// State labels from the initial model.
		String[] stateFeaturesV = { "0", "B-PER", "I-PER", "B-LOC", "I-LOC",
				"B-ORG", "I-ORG", "B-MISC", "I-MISC" };

		logger.info("Loading dataset...");
		// Load the trainset.
		Corpus trainset = new Corpus(trainFileName);
		int size = trainset.getNumberOfExamples();

		logger.info("Training initial model (supervised)...");
		// Supervised train an initial model.
		HmmTrainer hmmTrainer = new HmmTrainer();
		HmmModel modelSupervised = hmmTrainer.train(trainset,
				observationFeatureLabel, stateFeatureLabel, "0");

		// TODO this didn't work for CoNLL testset, but may work for SNOISY.
		// modelSupervised.setEmissionSmoothingProbability(10e-9);

		// Fill the tagged flag vector.
		Vector<Object> flags = new Vector<Object>(size);

		logger.info("Building tagged flags...");
		// The tokens tagged different of 0 are flagged as tagged. The rest are
		// flagged as untagged.
		for (int idxExample = 0; idxExample < size; ++idxExample) {
			DatasetExample example = trainset.getExample(idxExample);
			Vector<Boolean> flagsEx = new Vector<Boolean>(example.size());
			for (int token = 0; token < example.size(); ++token) {
				if (example.getFeatureValueAsString(token, stateFeatureLabel)
						.equals("0"))
					flagsEx.add(false);
				else
					flagsEx.add(true);
			}

			flags.add(flagsEx);
		}

		logger.info("Training semi-supervised model...");
		// Train an HMM model.
		SemiSupervisedHmmTrainer ssHmmTrainer = new SemiSupervisedHmmTrainer();
		ssHmmTrainer.setTaggedExampleFlags(flags);
		ssHmmTrainer.setInitialModel(modelSupervised);
		HmmModel model = ssHmmTrainer.train(trainset, observationFeatureLabel,
				stateFeatureLabel, stateFeaturesV, numIterations);

		// Save the model.
		model.save(modelFileName);
	}
}
