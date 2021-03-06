package br.pucrio.inf.learn.structlearning.discriminative.driver;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import br.pucrio.inf.learn.structlearning.discriminative.algorithm.OnlineStructuredAlgorithm.LearnRateUpdateStrategy;
import br.pucrio.inf.learn.structlearning.discriminative.algorithm.TrainingListener;
import br.pucrio.inf.learn.structlearning.discriminative.algorithm.perceptron.Perceptron;
import br.pucrio.inf.learn.structlearning.discriminative.application.dpgs.DPGSDataset;
import br.pucrio.inf.learn.structlearning.discriminative.application.dpgs.DPGSDualInference;
import br.pucrio.inf.learn.structlearning.discriminative.application.dpgs.DPGSInference;
import br.pucrio.inf.learn.structlearning.discriminative.application.dpgs.DPGSInput;
import br.pucrio.inf.learn.structlearning.discriminative.application.dpgs.DPGSModel;
import br.pucrio.inf.learn.structlearning.discriminative.application.dpgs.DPGSOutput;
import br.pucrio.inf.learn.structlearning.discriminative.data.DatasetException;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.FeatureEncoding;
import br.pucrio.inf.learn.structlearning.discriminative.data.encoding.StringMapEncoding;
import br.pucrio.inf.learn.structlearning.discriminative.driver.Driver.Command;
import br.pucrio.inf.learn.structlearning.discriminative.task.Inference;
import br.pucrio.inf.learn.structlearning.discriminative.task.Model;
import br.pucrio.inf.learn.util.CommandLineOptionsUtil;

/**
 * Driver to discriminatively train a dependency parser using perceptron-based
 * algorithms with second order features (grandparent and siblings).
 * 
 * @author eraldo
 * 
 */
public class TrainDPGS implements Command {

	/**
	 * Logging object.
	 */
	private static final Log LOG = LogFactory.getLog(TrainDPGS.class);

	@SuppressWarnings("static-access")
	@Override
	public void run(String[] args) {
		Options options = new Options();
		options.addOption(OptionBuilder
				.withLongOpt("train")
				.isRequired()
				.withArgName("filename")
				.hasArg()
				.withDescription(
						"Filename prefix with training dataset. It must "
								+ "exist three files with this prefix with "
								+ "the following sufixes: .grandparent, "
								+ ".leftsiblings and .rightsiblings.").create());
		options.addOption(OptionBuilder.withLongOpt("templates").isRequired()
				.withArgName("filename").hasArg()
				.withDescription("Filename prefix with templates.").create());
		options.addOption(OptionBuilder
				.withLongOpt("numepochs")
				.withArgName("integer")
				.hasArg()
				.withDescription(
						"Number of epochs: how many iterations over the"
								+ " training set.").create());
		options.addOption(OptionBuilder.withLongOpt("testconll")
				.withArgName("filename").hasArg()
				.withDescription("CoNLL-format test dataset.").create());
		options.addOption(OptionBuilder
				.withLongOpt("outputconll")
				.withArgName("filename")
				.hasArg()
				.withDescription(
						"Name of the CoNLL-format file to save the output.")
				.create());
		options.addOption(OptionBuilder.withLongOpt("test")
				.withArgName("filename").hasArg()
				.withDescription("Filename prefix with test dataset.").create());
		options.addOption(OptionBuilder.withLongOpt("script")
				.withArgName("path").hasArg()
				.withDescription("CoNLL evaluation script (eval.pl).").create());
		options.addOption(OptionBuilder
				.withLongOpt("perepoch")
				.withDescription(
						"The evaluation on the test corpus will "
								+ "be performed after each training epoch.")
				.create());
		options.addOption(OptionBuilder
				.withLongOpt("maxsteps")
				.withArgName("integer")
				.hasArg()
				.withDescription(
						"Maximum number of steps in the subgradient method.")
				.create());
		options.addOption(OptionBuilder
				.withLongOpt("beta")
				.withArgName("real number")
				.hasArg()
				.withDescription(
						"Fraction of the edge factor weights that "
								+ "are passed to the maximum branching "
								+ "algorithm, instead of the passing to "
								+ "the grandparent/siblings algorithm.")
				.create());
		options.addOption(OptionBuilder.withLongOpt("seed")
				.withArgName("integer").hasArg()
				.withDescription("Random number generator seed.").create());
		options.addOption(OptionBuilder
				.withLongOpt("lossweight")
				.withArgName("double")
				.hasArg()
				.withDescription(
						"Weight of the loss term in the inference objective function.")
				.create());
		options.addOption(OptionBuilder
				.withLongOpt("noavg")
				.withDescription(
						"Turn off the weight vector averaging, i.e.,"
								+ " the algorithm returns only the final weight "
								+ "vector instead of the average of each step "
								+ "vectors.").create());

		// Parse the command-line arguments.
		CommandLine cmdLine = null;
		PosixParser parser = new PosixParser();
		try {
			cmdLine = parser.parse(options, args);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			CommandLineOptionsUtil.usage(getClass().getSimpleName(), options);
		}

		// Print the list of options along the values provided by the user.
		CommandLineOptionsUtil.printOptionValues(cmdLine, options);

		// Training options.
		String trainPrefix = cmdLine.getOptionValue("train");
		String trainEdgeDatasetFileName = trainPrefix + ".edges";
		String trainGPDatasetFileName = trainPrefix + ".grandparent";
		String trainLSDatasetFileName = trainPrefix + ".siblings.left";
		String trainRSDatasetFileName = trainPrefix + ".siblings.right";
		String templatesPrefix = cmdLine.getOptionValue("templates");
		String templatesEdgeFileName = templatesPrefix + ".edges";
		String templatesGPFileName = templatesPrefix + ".grandparent";
		String templatesLSFileName = templatesPrefix + ".siblings.left";
		String templatesRSFileName = templatesPrefix + ".siblings.right";
		int numEpochs = Integer.parseInt(cmdLine.getOptionValue("numepochs",
				"10"));
		int maxSubgradientSteps = Integer.valueOf(cmdLine.getOptionValue(
				"maxsteps", "500"));
		double beta = Double.valueOf(cmdLine.getOptionValue("beta", "0.001"));
		// double lossWeight =
		// Double.parseDouble(cmdLine.getOptionValue("lossweight", "0d"));
		boolean averaged = !cmdLine.hasOption("noavg");
		String seedStr = cmdLine.getOptionValue("seed");

		// Test options.
		String testConllFileName = cmdLine.getOptionValue("testconll");
		String outputConllFilename = cmdLine.getOptionValue("outputconll");
		String testPrefix = cmdLine.getOptionValue("test");
		String testEdgeDatasetFilename = testPrefix + ".edges";
		String testGPDatasetFilename = testPrefix + ".grandparent";
		String testLSDatasetFilename = testPrefix + ".siblings.left";
		String testRSDatasetFilename = testPrefix + ".siblings.right";
		String script = cmdLine.getOptionValue("script");
		boolean evalPerEpoch = cmdLine.hasOption("perepoch");

		/*
		 * Options --testconll, --outputconll and --test must always be provided
		 * together.
		 */
		if ((testPrefix == null) != (testConllFileName == null)
				|| (outputConllFilename == null) != (testConllFileName == null)) {
			LOG.error("the options --testconll, --outputconll and --test "
					+ "must always be provided together (all or none)");
			System.exit(1);
		}

		DPGSDataset trainDataset = null;
		FeatureEncoding<String> featureEncoding = null;
		try {
			/*
			 * Create an empty and flexible feature encoding that will encode
			 * unambiguously all feature values. If the training dataset is big,
			 * this may not fit in memory and one should consider using a fixed
			 * encoding dictionary (based on test data or frequency on training
			 * data, for instance) or a hash-based encoding.
			 */
			featureEncoding = new StringMapEncoding();

			trainDataset = new DPGSDataset(new String[] { "bet-postag",
					"add-head-feats", "add-mod-feats" }, new String[] {
					"bet-hm-postag", "bet-hg-postag", "add-head-feats",
					"add-mod-feats", "add-gp-feats" }, new String[] {
					"bet-hm-postag", "bet-ms-postag", "add-head-feats",
					"add-mod-feats", "add-sib-feats" }, "|", featureEncoding);

			LOG.info(String.format("Loading training edge dataset (%s)...",
					trainEdgeDatasetFileName));
			trainDataset.loadEdgeFactors(trainEdgeDatasetFileName);

			LOG.info(String.format(
					"Loading training left siblings dataset (%s)...",
					trainLSDatasetFileName));
			trainDataset.loadSiblingsFactors(trainLSDatasetFileName);

			LOG.info(String.format(
					"Loading training right siblings dataset (%s)...",
					trainRSDatasetFileName));
			trainDataset.loadSiblingsFactors(trainRSDatasetFileName);

			/*
			 * Grandparent factors shall be the last ones to avoid problems with
			 * short sentences (1 ordinary token), since grandparent factors do
			 * not exist for such short sentences.
			 */
			LOG.info(String.format(
					"Loading training grandparent dataset (%s)...",
					trainGPDatasetFileName));
			trainDataset.loadGrandparentFactors(trainGPDatasetFileName);

			// Set modifier variables in all output structures.
			trainDataset.setModifierVariables();

			// Template-based model.
			LOG.info("Allocating initial model...");

			// Model.
			DPGSModel model = new DPGSModel(0);

			// Templates.
			model.loadEdgeTemplates(templatesEdgeFileName, trainDataset);
			model.loadGrandparentTemplates(templatesGPFileName, trainDataset);
			model.loadLeftSiblingsTemplates(templatesLSFileName, trainDataset);
			model.loadRightSiblingsTemplates(templatesRSFileName, trainDataset);

			// Generate derived features from templates.
			model.generateFeatures(trainDataset);

			// Inference algorithm for training.
			DPGSInference inference = new DPGSInference(
					trainDataset.getMaxNumberOfTokens());

			// Learning algorithm.
			Perceptron alg = new Perceptron(inference, model, numEpochs, 1d,
					true, averaged, LearnRateUpdateStrategy.NONE);

			if (seedStr != null)
				// User provided seed to random number generator.
				alg.setSeed(Long.parseLong(seedStr));

			if (testConllFileName != null && evalPerEpoch) {
				LOG.info("Loading test factors...");
				DPGSDataset testset = new DPGSDataset(trainDataset);
				testset.loadEdgeFactors(testEdgeDatasetFilename);
				testset.loadSiblingsFactors(testLSDatasetFilename);
				testset.loadSiblingsFactors(testRSDatasetFilename);
				testset.loadGrandparentFactors(testGPDatasetFilename);
				testset.setModifierVariables();
				model.generateFeatures(testset);

				LOG.info("Evaluating...");

				// Use dual inference algorithm for testing.
				DPGSDualInference inferenceDual = new DPGSDualInference(
						testset.getMaxNumberOfTokens());
				inferenceDual
						.setMaxNumberOfSubgradientSteps(maxSubgradientSteps);
				inferenceDual.setBeta(beta);

				// // TODO test
				// DPGSInference inferenceDual = new DPGSInference(
				// testset.getMaxNumberOfTokens());
				// inferenceDual.setCopyPredictionToParse(true);

				EvaluateModelListener eval = new EvaluateModelListener(script,
						testConllFileName, outputConllFilename, testset,
						averaged, inferenceDual);
				eval.setQuiet(true);
				alg.setListener(eval);
			}

			LOG.info("Training model...");
			// Train model.
			alg.train(trainDataset.getInputs(), trainDataset.getOutputs());

			LOG.info(String.format("# updated parameters: %d",
					model.getNumberOfUpdatedParameters()));

			if (testConllFileName != null && !evalPerEpoch) {
				LOG.info("Loading test factors...");
				DPGSDataset testset = new DPGSDataset(trainDataset);
				testset.loadEdgeFactors(testEdgeDatasetFilename);
				testset.loadSiblingsFactors(testLSDatasetFilename);
				testset.loadSiblingsFactors(testRSDatasetFilename);
				testset.loadGrandparentFactors(testGPDatasetFilename);
				testset.setModifierVariables();
				model.generateFeatures(testset);

				LOG.info("Evaluating...");

				// Use dual inference algorithm for testing.
				DPGSDualInference inferenceDual = new DPGSDualInference(
						testset.getMaxNumberOfTokens());
				inferenceDual
						.setMaxNumberOfSubgradientSteps(maxSubgradientSteps);
				inferenceDual.setBeta(beta);

				// // TODO test
				// DPGSInference inferenceDual = new DPGSInference(
				// testset.getMaxNumberOfTokens());
				// inferenceDual.setCopyPredictionToParse(true);

				EvaluateModelListener eval = new EvaluateModelListener(script,
						testConllFileName, outputConllFilename, testset, false,
						inferenceDual);
				eval.setQuiet(true);
				eval.afterEpoch(inferenceDual, model, -1, -1d, -1);
			}

			LOG.info("Training done!");

		} catch (Exception e) {
			LOG.error("Error during training", e);
			System.exit(1);
		}
	}

	public static void evaluateWithConllScripts(String script,
			String conllGolden, String conllPredicted, boolean quiet)
			throws IOException, CommandException, InterruptedException {
		// Command to evaluate the predicted information.
		String cmd = String.format("perl %s -g %s -s %s%s", script,
				conllGolden, conllPredicted, (quiet ? " -q" : ""));
		execCommandAndRedirectOutputAndError(cmd, null);
	}

	/**
	 * Execute the given system command and redirects its standard and error
	 * outputs to the standard and error outputs of the JVM process.
	 * 
	 * @param command
	 * @param path
	 * @throws IOException
	 * @throws CommandException
	 * @throws InterruptedException
	 */
	private static void execCommandAndRedirectOutputAndError(String command,
			File path) throws IOException, CommandException,
			InterruptedException {
		String line;

		// Execute command.
		LOG.info("Running command: " + command);
		Process p = Runtime.getRuntime().exec(command, null, path);

		// Redirect standard output of process.
		BufferedReader out = new BufferedReader(new InputStreamReader(
				p.getInputStream()));
		while ((line = out.readLine()) != null)
			System.out.println(line);
		out.close();

		// Redirect error output of process.
		BufferedReader error = new BufferedReader(new InputStreamReader(
				p.getErrorStream()));
		while ((line = error.readLine()) != null)
			System.err.println(line);
		error.close();

		if (p.waitFor() != 0)
			throw new CommandException("Command exit with non-zero status");
	}

	private static class CommandException extends Exception {
		/**
		 * Auto-generated serial version ID.
		 */
		private static final long serialVersionUID = 6582860853130630178L;

		public CommandException(String message) {
			super(message);
		}
	}

	/**
	 * Training listener to evaluate models after each epoch.
	 * 
	 * @author eraldof
	 * 
	 */
	private static class EvaluateModelListener implements TrainingListener {

		private String script;

		private String conllGolden;

		private String conllPredicted;

		private DPGSDataset testset;

		private DPGSOutput[] predicteds;

		private boolean averaged;

		private boolean quiet;

		private Inference inference;

		public EvaluateModelListener(String script, String conllGolden,
				String conllPredicted, DPGSDataset testset, boolean averaged,
				Inference inference) {
			this.script = script;
			this.conllGolden = conllGolden;
			this.conllPredicted = conllPredicted;
			this.testset = testset;
			this.averaged = averaged;
			this.inference = inference;

			// Allocate output sequences for predictions.
			int numExs = testset.getNumberOfExamples();
			DPGSInput[] inputs = testset.getInputs();
			this.predicteds = new DPGSOutput[numExs];
			for (int idx = 0; idx < numExs; ++idx)
				predicteds[idx] = inputs[idx].createOutput();
		}

		public void setQuiet(boolean val) {
			quiet = val;
		}

		@Override
		public boolean beforeTraining(Inference impl, Model curModel) {
			return true;
		}

		@Override
		public void afterTraining(Inference impl, Model curModel) {
		}

		@Override
		public boolean beforeEpoch(Inference impl, Model curModel, int epoch,
				int iteration) {
			return true;
		}

		@Override
		public boolean afterEpoch(Inference inferenceImpl, Model model,
				int epoch, double loss, int iteration) {

			if (averaged) {
				try {
					// Clone the current model to average it, if necessary.
					LOG.info(String.format(
							"Cloning current model with %d parameters...",
							((DPGSModel) model).getParameters().size()));
					model = (Model) model.clone();
				} catch (CloneNotSupportedException e) {
					LOG.error(
							String.format(
									"Cloning current model with %d parameters on epoch %d and iteration %d",
									((DPGSModel) model).getParameters().size(),
									epoch, iteration), e);
					return true;
				}

				/*
				 * The averaged perceptron averages the final model only in the
				 * end of the training process, hence we need to average the
				 * temporary model here in order to have a better picture of its
				 * current (intermediary) performance.
				 */
				LOG.info("Averaging model...");
				model.average(iteration);
			}

			LOG.info("Predicting outputs...");

			// Use other inference if it has been given in constructor.
			if (inference != null)
				inferenceImpl = inference;
			// Fill the list of predicted outputs.
			DPGSInput[] inputs = testset.getInputs();
			// DPOutput[] outputs = testset.getOutputs();
			for (int idx = 0; idx < inputs.length; ++idx) {
				// Predict (tag the output sequence).
				inferenceImpl.inference(model, inputs[idx], predicteds[idx]);
				if ((idx + 1) % 100 == 0) {
					System.out.print(".");
					System.out.flush();
				}
			}

			// TODO test
			LOG.info(String.format("# subgradient steps / prediction: %d",
					((DPGSDualInference) inferenceImpl)
							.getMaxNumberOfSubgradientSteps()));

			try {
				// Delete previous epoch output file if it exists.
				File o = new File(conllPredicted);
				if (o.exists())
					o.delete();

				LOG.info(String
						.format("Saving input CoNLL file (%s) to output file (%s) with predicted columns",
								conllGolden, conllPredicted));
				testset.save(conllGolden, conllPredicted, predicteds);

				try {
					LOG.info("Evaluation after epoch " + epoch + ":");
					// Execute CoNLL evaluation scripts.
					evaluateWithConllScripts(script, conllGolden,
							conllPredicted, quiet);
				} catch (Exception e) {
					LOG.error("Running evaluation scripts", e);
				}

			} catch (IOException e) {
				LOG.error("Saving test file with predicted column", e);
			} catch (DatasetException e) {
				LOG.error("Saving test file with predicted column", e);
			}

			return true;
		}

		@Override
		public void progressReport(Inference impl, Model curModel, int epoch,
				double loss, int iteration) {
		}
	}
}
