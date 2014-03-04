package fr.loria.synalp.jtrans.viterbi;

import edu.cmu.sphinx.frontend.*;
import edu.cmu.sphinx.frontend.util.AudioFileDataSource;
import edu.cmu.sphinx.linguist.acoustic.*;
import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import fr.loria.synalp.jtrans.facade.Cache;
import fr.loria.synalp.jtrans.speechreco.grammaire.Grammatiseur;
import fr.loria.synalp.jtrans.speechreco.s4.*;
import fr.loria.synalp.jtrans.utils.TimeConverter;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * State graph representing a grammar.
 */
public class StateGraph {

	/**
	 * Maximum number of transitions an HMM state may have.
	 */
	/*
	If this value ever has to exceed 127 (overkill), byte reads (for
	transitions) will need to be peppered with "& 0xFF" (no unsigned bytes in
	Java). If it ever has to exceed 255 (way overkill), be sure to change
	the type of the inCount/outCount arrays.
	*/
	public static final int MAX_TRANSITIONS = 32;

	/** Pattern for non-phone grammar tokens. */
	public final static Pattern NONPHONE_PATTERN =
			Pattern.compile("^[^a-zA-Z]$");

	/** All HMM states in the grammar. */
	private HMMState[] states;
	// Note: using a set of unique states barely improves performance since
	// scores are typically cached (see ScoreCachingSenone.getScore)

	/**
	 * Number of transitions for each HMM state.
	 * Values in this array should not exceed MAX_TRANSITIONS, otherwise an
	 * index out of bounds exception will eventually be thrown.
	 */
	private byte[] outCount;

	/** Transition matrix: successor IDs */
	private int[][] outState;

	/** Transition matrix: probabilities */
	private float[][] outProb;

	/**
	 * Number of incoming transitions for each HMM state
	 * Values in this array should not exceed MAX_TRANSITIONS, otherwise an
	 * index out of bounds exception will eventually be thrown.
	 */
	private final byte[] inCount;

	/** HMM State IDs for each incoming transition */
	private final int[][] inState;

	/** Probability of each incoming transition */
	private final float[][] inProb;

	/** Total number of non-empty phones contained in the grammar. */
	private final int nPhones;

	/** Total number of HMM states in the grammar. */
	private final int nStates;

	/** Insertion point for new states in the states array. */
	private int insertionPoint;

	/** Human-readable words at the basis of this grammar */
	private final String[] words;

	/**
	 * Indices of the initial HMM state of each word.
	 * All potential pronunciations for a given word are inserted contiguously
	 * in the states array. The order in which the HMM states are inserted
	 * reflects the order of the words.
	 */
	private final int[] wordBoundaries;

	/**
	 * Trims leading and trailing whitespace then splits around whitespace.
	 */
	public static String[] trimSplit(String text) {
		return text.trim().split("\\s+");
	}


	/**
	 * Counts phones in a list of words.
	 */
	public static int countPhones(String[] words) {
		// mandatory initial and final silences
		// plus one optional silence between each word
		int count = 1 + words.length;

		for (String w: words) {
			String rule = Grammatiseur.getGrammatiseur().getGrammar(w);
			for (String token: trimSplit(rule))
				if (!NONPHONE_PATTERN.matcher(token).matches())
					count++;
		}

		return count;
	}


	/**
	 * Parses a grammar rule and adds the corresponding states to the vector.
	 * The states are bound together as needed, effectively creating a graph.
	 * @param tokenIter iterator on rule tokens
	 * @param tails IDs of states that have no outbound transitions yet (they
	 *              are always the 3rd state in a phone). New states will be
	 *              chained to tail states. IMPORTANT: this set is modified by
	 *              this method! After the method has run, the set contains the
	 *              new tails in the graph.
	 * @return token an unknown token that stopped the parsing of the grammar,
	 * or null if the entire token stream has been parsed successfully
	 */
	private String parseRule(
			Iterator<String> tokenIter,
			Set<Integer> tails,
			AcousticModel acMod,
			UnitManager unitMgr)
	{
		while (tokenIter.hasNext()) {
			String token = tokenIter.next();

			if (!NONPHONE_PATTERN.matcher(token).matches()) {
				// Compulsory 1-token path
				// Replace existing tails

				String phone = PhoneticForcedGrammar.convertPhone(token);

				// Bind tails to the 1st state that is going to be created
				for (Integer parentId: tails) {
					assert outCount[parentId] < MAX_TRANSITIONS - 1:
							"too many transitions";
					outState[parentId][outCount[parentId]++] = insertionPoint;
				}

				// New sole tail is future 3rd state
				tails.clear();
				tails.add(insertionPoint + 2);

				// Create the actual states
				insertStateTriplet(phone, acMod, unitMgr);
			}

			else if (token.equals("(")) {
				// Compulsory multiple choice path
				// Replace existing tails

				Set<Integer> tailsCopy = new HashSet<Integer>(tails);
				tails.clear();

				do {
					Set<Integer> newTails = new HashSet<Integer>(tailsCopy);
					token = parseRule(tokenIter, newTails, acMod, unitMgr);
					tails.addAll(newTails);
				} while (token.equals("|"));
				assert token.equals(")");
			}

			else if (token.equals("[")) {
				// Optional path
				// Append new tails to existing tails

				Set<Integer> subTails = new HashSet<Integer>(tails);
				token = parseRule(tokenIter, subTails, acMod, unitMgr);
				tails.addAll(subTails);
				assert token.equals("]");
			}

			else {
				return token;
			}
		}

		return null;
	}


	/**
	 * Convenience method to parse a rule from a string.
	 * See the main parseRule method for more information.
	 */
	private String parseRule(
			String rule,
			Set<Integer> tails,
			AcousticModel acMod,
			UnitManager unitMgr)
	{
		return parseRule(
				Arrays.asList(trimSplit(rule)).iterator(),
				tails,
				acMod,
				unitMgr);
	}


	/**
	 * Inserts three emitting HMM states corresponding to a phone.
	 * Binds the three states together. The first state has no inbound
	 * transitions and the third state has no outbound transitions.
	 */
	private int insertStateTriplet(
			String phone,
			final AcousticModel acMod,
			final UnitManager unitMgr)
	{
		System.out.println("inserting state triplet for '" + phone + "'");

		// find HMM for this phone
		HMM hmm = acMod.lookupNearestHMM(
				unitMgr.getUnit(phone), HMMPosition.UNDEFINED, false);

		// add phone states
		for (int i = 0; i < 3; i++) {
			int j = insertionPoint + i;
			HMMState state = hmm.getState(i);

			assert state.isEmitting();
			assert !state.isExitState();
			assert state.getSuccessors().length == 2;

			states[j] = state;
			outState[j][0] = j;
			if (i < 2) {
				outState[j][1] = j+1;
				outCount[j] = 2;
			} else {
				outCount[j] = 1;
			}

			for (HMMStateArc arc: state.getSuccessors()) {
				HMMState arcState = arc.getHMMState();
				if (i == 2 && arcState.isExitState())
					continue;

				float p = arc.getLogProbability();
				if (arcState == state) {
					outProb[j][0] = p;
				} else {
					assert i != 2;
					assert !arcState.isExitState();
					assert arcState == hmm.getState(i+1);
					outProb[j][1] = p;
				}
			}
		}

		insertionPoint += 3;
		return insertionPoint - 1;
	}


	/**
	 * Sets uniform inter-phone probabilities on the last state of an HMM.
	 * @param stateId ID of the last (3rd) state of an HMM
	 */
	private void setUniformInterPhoneTransitionProbabilities(int stateId) {
		assert stateId % 3 == 2 : "must be a third state";

		if (outCount[stateId] < 2)
			return;

		assert outProb[stateId][0] != 0f : "loop probability must be initialized";
		assert outProb[stateId][1] == 0f : "non-loop probabilities must be uninitialized";

		LogMath lm = HMMModels.getLogMath();
		double linearLoopProb = lm.logToLinear(outProb[stateId][0]);
		float p = lm.linearToLog(
				(1f - linearLoopProb) / (double)(outCount[stateId] - 1));

		for (byte j = 1; j < outCount[stateId]; j++)
			outProb[stateId][j] = p;
	}


	/**
	 * Constructs a grammar vector from a list of words.
	 */
	public StateGraph(
			String text,
			AcousticModel acMod,
			UnitManager unitMgr)
	{
		words = trimSplit(text);
		wordBoundaries = new int[words.length];

		nPhones = countPhones(words);
		nStates = 3 * nPhones;

		states = new HMMState[nStates];

		outCount = new byte  [nStates];
		outState = new int   [nStates][MAX_TRANSITIONS];
		outProb  = new float [nStates][MAX_TRANSITIONS];

		inCount = new byte   [nStates];
		inState = new int    [nStates][MAX_TRANSITIONS];
		inProb  = new float  [nStates][MAX_TRANSITIONS];

		//----------------------------------------------------------------------
		// Build state graph

		Grammatiseur gram = Grammatiseur.getGrammatiseur();
		Set<Integer> tails = new HashSet<Integer>();

		// add initial mandatory silence
		parseRule("SIL", tails, acMod, unitMgr);

		for (int i = 0; i < words.length; i++) {
			if (i > 0) {
				// optional silence between two words
				parseRule("[ SIL ]", tails, acMod, unitMgr);
			}

			// Word actually starts after optional silence
			wordBoundaries[i] = insertionPoint;

			String rule = gram.getGrammar(words[i]);
			System.out.println("Rule: " + rule);
			assert rule != null;
			assert !rule.isEmpty();

			String token = parseRule(rule, tails, acMod, unitMgr);
			assert token == null;
		}

		// add final mandatory silence
		parseRule("SIL", tails, acMod, unitMgr);

		assert insertionPoint == nStates;
		// correct inter-phone transition probabilities
		for (int i = 2; i < nStates; i += 3) {
			setUniformInterPhoneTransitionProbabilities(i);
		}

		// last state can loop forever
		outProb[nStates-1][0] = 0f; // log domain

		assert insertionPoint == nStates : "predicted state count not met";
		assert outCount[nStates-1] == 1 : "last state can only have 1 transition";
		assert outState[nStates-1][0] == nStates - 1 : "last state can only transition to itself";

		//----------------------------------------------------------------------
		// Prepare incoming transitions (predecessors) for each state

		int maxPredec = 0;

		for (int parent = 0; parent < nStates; parent++) {
			for (byte childTID = 0; childTID < outCount[parent]; childTID++) {
				int child = outState[parent][childTID];
				int childPredecID = inCount[child]++;
				inState[child][childPredecID] = parent;
				inProb[child][childPredecID] = outProb[parent][childTID];
				if (inCount[child] > maxPredec)
					maxPredec = inCount[child];
			}
		}

		System.out.println("Max predecessor count: " + maxPredec);
	}


	/**
	 * Creates a StateGraph using JTrans's default configuration for Sphinx4.
	 */
	public static StateGraph createStandardStateGraph(String words) {
		ConfigurationManager cm = new ConfigurationManager("sr.cfg");

		UnitManager unitmgr = (UnitManager)cm.lookup("unitManager");
		assert unitmgr != null;

		AcousticModel acmod = HMMModels.getAcousticModels();

		return new StateGraph(words, acmod, unitmgr);
	}


	/**
	 * Dumps a GraphViz/DOT representation of the vector.
	 */
	public void dumpDot(Writer w) throws IOException {
		LogMath lm = HMMModels.getLogMath();

		w.write("digraph {");

		for (int i = 0; i < nStates; i++) {
			HMMState s = states[i];
			w.write(String.format("\nnode%d [ label=\"%s %d\" ]", i,
					s.getHMM().getBaseUnit().getName(), s.getState()));
			for (byte j = 0; j < outCount[i]; j++) {
				w.write(String.format("\nnode%d -> node%d [ label=%f ]",
						i, outState[i][j], lm.logToLinear(outProb[i][j])));
			}
		}

		w.write("\n}");
		w.flush();
	}


	/**
	 * Finds the most likely predecessor of each HMM state for each audio frame
	 * (using the Viterbi algorithm).
	 *
	 * Each iteration builds upon the likelihoods found in the previous
	 * iteration, as well as the score given by every HMM state for each audio
	 * frame.
	 *
	 * After the last iteration, we obtain a full table of the most likely
	 * predecessors of each state and for each frame. Since there is only one
	 * possible final state, this table tells us which state most likely
	 * transitioned to the final state in the second-to-last frame. From there,
	 * we can find the most likely predecessor of *that* state in the
	 * third-to-last frame... and so on and so forth until we have traced the
	 * most likely path back to the initial state.
	 *
	 * All this method actually does is computing the likelihoods for each frame
	 * and storing them in a swap file. The pathfinding process is completed by
	 * backtrack().
	 *
	 * @see StateGraph#backtrack second part of the pathfinding process
	 * @see SwapInflater
	 * @param mfcc audio source
	 * @param swapWriter object that commits likelihoods to a swap file
	 *                   or buffer
	 * @param startFrame first frame to analyze
	 * @param endFrame last frame to analyze. Use a negative number to use
	 *                 all frames in the audio source.
	 */
	public void viterbi(
			S4mfccBuffer mfcc,
			SwapDeflater swapWriter,
			int startFrame,
			int endFrame)
			throws IOException, InterruptedException
	{
		// Probability vectors
		float[] vpf = new float[nStates]; // vector for previous frame (read-only)
		float[] vcf = new float[nStates]; // vector for current frame (write-only)

		// ID of the incoming transition that yielded bestReachProb for each state
		byte[] bestInTrans = new byte[nStates];

		// Initialize probability vector
		// We only have one initial state (state #0), probability 1
		Arrays.fill(vpf, Float.NEGATIVE_INFINITY);
		vpf[0] = 0; // Probabilities are in the log domain

		mfcc.gotoFrame(startFrame);
		int f = startFrame;
		while (!mfcc.noMoreFramesAvailable && (endFrame < 0 || f <= endFrame)) {
			Data frame = mfcc.getData();
			if (frame instanceof DataStartSignal || frame instanceof DataEndSignal)
				continue;
			f++;

			for (int i = 0; i < nStates; i++) {
				// Emission probability (frame score)
				float emission = states[i].getScore(frame);

				assert inCount[i] >= 1;

				// Probability to reach a state given the previous vector v
				// i.e. max(P(k -> i) * v[k]) for each predecessor k of state #i
				float bestReachProb;

				// Initialize with first incoming transition
				bestReachProb = inProb[i][0] + vpf[inState[i][0]]; // log domain
				bestInTrans[i] = 0;

				// Find best probability among all incoming transitions
				for (byte j = 1; j < inCount[i]; j++) {
					float p = inProb[i][j] + vpf[inState[i][j]]; // log domain
					if (p > bestReachProb) {
						bestReachProb = p;
						bestInTrans[i] = j;
					}
				}

				vcf[i] = emission + bestReachProb; // log domain
			}

			swapWriter.write(bestInTrans);

			// swap vectors
			float[] temp = vcf;
			vcf = vpf;
			vpf = temp;
		}

		for (int s = 0; s < nStates; s++) {
			System.out.println("V[" + s + "] " + vpf[s]);
		}

		swapWriter.close();
	}


	/**
	 * Finds the most likely path between the initial and final states using the
	 * table of most likely predecessors found by viterbi().
	 *
	 * @see StateGraph#viterbi first part of the pathfinding process
	 * @param swapReader reader for the swap file produced by viterbi()
	 * @return A time line of the most likely state at each frame. Given as an
	 * array of state IDs, with array indices being frame numbers relative to
	 * the first frame given to StateGraph#viterbi.
	 */
	private int[] backtrack(SwapInflater swapReader) throws IOException {
		System.out.println("Backtracking...");

		int pathLead = nStates-1;
		int[] timeline = new int[swapReader.getFrameCount()];
		for (int f = timeline.length-1; f >= 0; f--) {
			byte transID = swapReader.getIncomingTransition(f, pathLead);
			pathLead = inState[pathLead][transID];
			timeline[f] = pathLead;
			assert pathLead >= 0;
		}

		System.out.println("Note: only initial states are shown below");
		System.out.println("    TIME   STATE#     UNIT");
		for (int f = 0; f < timeline.length; f++) {
			if (f == 0 || timeline[f-1]/3 != timeline[f]/3) {
				System.out.println(String.format("%8.2f %8d %8s",
						TimeConverter.frame2sec(f),
						timeline[f],
						states[timeline[f]].getHMM().getBaseUnit()));
			}
		}

		System.out.println("\n    TIME         WORD       BOUNDARY");
		int pw = -1;
		int w = -1;
		for (int f = 0; f < timeline.length; f++) {
			int frameState = timeline[f];
			while (w+1 < words.length && wordBoundaries[w+1] < frameState) {
				w++;
			}
			if (w != pw) {
				System.out.println(String.format("%8.2f %16s %8d",
						TimeConverter.frame2sec(f),
						words[w],
						wordBoundaries[w]));
				pw = w;
			}
		}

		return timeline;
	}


	/**
	 *
	 * @param swapReader
	 * @param startFrame
	 * @return An array containing two alignments: the first element is the
	 * word alignment, the second element is the phone alignment.
	 */
	public Alignment[] getAlignments(
			SwapInflater swapReader,
			int startFrame)
			throws IOException
	{
		int[] timeline = backtrack(swapReader);

		Alignment wordAl = getWordAlignment(timeline);
		Alignment phoneAl = getPhoneAlignment(timeline);

		wordAl.adjustOffset(startFrame);
		phoneAl.adjustOffset(startFrame);

		return new Alignment[] { wordAl, phoneAl };
	}


	private Alignment getWordAlignment(int[] timeline) {
		Alignment al = new Alignment();
		int prevWord = -1;
		int prevWordFrame0 = -1;
		int currWord = -1;

		for (int f = 0; f < timeline.length; f++) {
			int frameState = timeline[f];
			while (currWord+1 < words.length &&
					wordBoundaries[currWord+1] <= frameState)
			{
				currWord++;
			}

			if (currWord != prevWord) {
				if (prevWord >= 0) {
					al.addRecognizedSegment(
							words[prevWord], prevWordFrame0, f-1);
				}
				prevWord = currWord;
				prevWordFrame0 = f;
			}
		}

		// Add last word
		if (prevWord >= 0) {
			al.addRecognizedSegment(
					words[prevWord], prevWordFrame0, timeline.length-1);
		}

		return al;
	}


	private Alignment getPhoneAlignment(int[] timeline) {
		Alignment al = new Alignment();
		int prevState = -1;
		int state0Frame0 = -1; // 1st frame of the 1st state of the ongoing phone

		for (int f = 0; f < timeline.length; f++) {
			int currState = timeline[f];

			if (f == 0 || prevState/3 != currState/3) {
				if (prevState >= 0) {
					al.addRecognizedSegment(
							states[prevState].getHMM().getBaseUnit().getName(),
							state0Frame0,
							f-1);
				}
				prevState = currState;
				state0Frame0 = f;
			}
		}

		// Add last phone
		if (prevState >= 0) {
			al.addRecognizedSegment(
					states[prevState].getHMM().getBaseUnit().getName(),
					state0Frame0,
					timeline.length-1);
		}

		return al;
	}


	/** Returns the total number of HMM states in the grammar. */
	public int getStateCount() {
		return nStates;
	}


	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("USAGE: StateGraph <SOUNDFILE.WAV> <\"transcription\">");
			System.exit(1);
		}

		final String wavpath = args[0];

		final String words = new Scanner(new File(args[1])).useDelimiter("\\Z")
				.next().replaceAll("[\\n\\r\u001f]", " ");

		StateGraph gv = StateGraph.createStandardStateGraph(words);
		System.out.println("PHONE COUNT: " + gv.nPhones);
		System.out.println("GRAPH SIZE: " + gv.nStates);
		gv.dumpDot(new FileWriter("grammar_vector.dot"));

		AudioFileDataSource afds = new AudioFileDataSource(3200, null);
		afds.setAudioFile(new File(wavpath), null);
		S4mfccBuffer mfcc = new S4mfccBuffer();
		mfcc.setSource(S4ForceAlignBlocViterbi.getFrontEnd(afds));

		System.out.println("Starting Viterbi...");

		File swapFile = Cache.getCacheFile("backtrack", "swp", words);
		File indexFile = Cache.getCacheFile("backtrack", "idx", words);

		boolean quick = false;
		PageIndex index;
		if (!quick) {
			long t0 = System.currentTimeMillis();
			SwapDeflater swapper = SwapDeflater.getSensibleSwapDeflater(
					gv.nStates,
					new FileOutputStream(swapFile),
					true);
			gv.viterbi(mfcc, swapper, 0, -1);
			System.out.println("VITERBI TOOK " + (System.currentTimeMillis()-t0)/1000L + " SECONDS");
			index = swapper.getIndex();
			index.serialize(new FileOutputStream(indexFile));
		} else {
			index = PageIndex.deserialize(new FileInputStream(indexFile));
		}
		System.out.println("FRAME COUNT: " + index.getFrameCount());
		gv.backtrack(new SwapInflater(swapFile, index));
		System.out.println("done");
	}

}
