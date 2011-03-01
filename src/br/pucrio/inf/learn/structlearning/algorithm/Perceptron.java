package br.pucrio.inf.learn.structlearning.algorithm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import br.pucrio.inf.learn.structlearning.data.ExampleInput;
import br.pucrio.inf.learn.structlearning.data.ExampleOutput;
import br.pucrio.inf.learn.structlearning.task.Model;

/**
 * Perceptron-trained linear classifier with structural examples.
 * 
 * @author eraldof
 * 
 */
public class Perceptron {

	private static final Log LOG = LogFactory.getLog(Perceptron.class);

	/**
	 * Task-specific model.
	 */
	protected Model model;

	/**
	 * Learning rate.
	 */
	protected double learningRate;

	/**
	 * Number of iterations.
	 */
	protected int numberOfIterations;

	/**
	 * This is the current iteration but counting one iteration for each
	 * example. This is necessary for the averaged-Perceptron implementation.
	 */
	protected int averagingIteration;

	/**
	 * Create a perceptron to train the given initial model using the default
	 * Collins' learning rate (1) and the default number of iterations (10).
	 * 
	 * @param initialModel
	 */
	public Perceptron(Model initialModel) {
		this(initialModel, 10, 1d);
	}

	/**
	 * Create a perceptron to train the given initial model using the given
	 * number of iterations and learning rate.
	 * 
	 * @param initialModel
	 * @param numberOfIterations
	 * @param learningRate
	 */
	public Perceptron(Model initialModel, int numberOfIterations,
			double learningRate) {
		this.model = initialModel;
		this.numberOfIterations = numberOfIterations;
		this.learningRate = learningRate;
	}

	public double getLearningRate() {
		return learningRate;
	}

	public void setLearningRate(double learningRate) {
		this.learningRate = learningRate;
	}

	public int getNumberOfIterations() {
		return numberOfIterations;
	}

	public void setNumberOfIterations(int numberOfIterations) {
		this.numberOfIterations = numberOfIterations;
	}

	/**
	 * Train the model with the given examples, iterating according to the
	 * number of iterations. Corresponding inputs and outputs must be in the
	 * same order.
	 * 
	 * @param inputs
	 * @param outputs
	 */
	public void train(ExampleInput[] inputs, ExampleOutput[] outputs) {

		// Create a predicted output object for each example.
		ExampleOutput[] predicteds = new ExampleOutput[outputs.length];
		int idx = 0;
		for (ExampleInput input : inputs) {
			predicteds[idx] = input.createOutput();
			++idx;
		}

		averagingIteration = 0;
		for (int iter = 0; iter < numberOfIterations; ++iter) {
			LOG.info("Perceptron iteration: " + iter + "...");
			// Iterate over the training examples, updating the weight vector.
			idx = 0;
			for (ExampleInput input : inputs) {
				// Update the current model weights according with the predicted
				// output for this training example.
				train(input, outputs[idx], predicteds[idx]);
				// Averaged-Perceptron: account the updates into the averaged
				// weights.
				model.posIteration(averagingIteration);
				++averagingIteration;
				++idx;
			}
		}

		// Averaged-Perceptron: average the final weights.
		model.posTraining(averagingIteration);
	}

	/**
	 * Update the current model using the two given outputs for one input.
	 * 
	 * @param input
	 * @param correctOutput
	 * @param predictedOutput
	 */
	public void train(ExampleInput input, ExampleOutput correctOutput,
			ExampleOutput predictedOutput) {

		// Predict the best output with the current mobel.
		model.inference(input, predictedOutput);

		// Update the current model.
		model.update(input, correctOutput, predictedOutput, learningRate);

	}

}
