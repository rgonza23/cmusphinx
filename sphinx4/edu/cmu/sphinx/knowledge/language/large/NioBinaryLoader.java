/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electronic Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

package edu.cmu.sphinx.knowledge.language.large;

import edu.cmu.sphinx.knowledge.dictionary.Dictionary;
import edu.cmu.sphinx.knowledge.language.LanguageModel;

import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.Timer;
import edu.cmu.sphinx.util.Utilities;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.net.URL;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;

import java.nio.channels.FileChannel;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import java.util.regex.Pattern;


/**
 * Reads a binary language model file generated by the 
 * CMU-Cambridge Statistical Language Modelling Toolkit.
 * 
 * Note that all probabilites in the grammar are stored in LogMath log
 * base format. Language Probabilties in the language model file are
 * stored in log 10  base. They are converted to the LogMath logbase.
 */
class NioBinaryLoader {

    private static final String DARPA_LM_HEADER = "Darpa Trigram LM";

    private static final int LOG2_BIGRAM_SEGMENT_SIZE_DEFAULT = 9;

    private static final float MIN_PROBABILITY = -99.0f;

    private static final String PROP_PREFIX = 
    "edu.cmu.sphinx.knowledge.language.large.NioBinaryLoader.";
    
    /**
     * Sphinx property for whether to apply the language weight and
     * word insertion probability.
     */
    public static final String PROP_APPLY_LANGUAGE_WEIGHT_AND_WIP =
    PROP_PREFIX + "applyLanguageWeightAndWip";
        
    /**
     * Default value for PROP_APPLY_LANGUAGE_WEIGHT_AND_WIP.
     */
    public static final boolean PROP_APPLY_LANGUAGE_WEIGHT_AND_WIP_DEFAULT =
    false;

    private static Pattern stringPattern = Pattern.compile("\0");

    private SphinxProperties props;
    private LogMath logMath;
    private int maxNGram = 3;

    private UnigramProbability[] unigrams;
    private String[] words;

    private int bigramOffset;
    private int trigramOffset;
    private int numberUnigrams;
    private int numberBigrams;
    private int numberTrigrams;
    private int logBigramSegmentSize;
    private int startWordID;

    private int[] trigramSegmentTable;

    private float[] bigramProbTable;
    private float[] trigramBackoffTable;
    private float[] trigramProbTable;

    private RandomAccessFile file;

    private boolean bigEndian = true;
    private boolean applyLanguageWeightAndWip;

    private Timer headerTimer;
    private Timer unigramTimer;
    private Timer uwTimer;
    private Timer tablesTimer;
    private Timer stringTimer;
    private Timer parseTimer;
    private Timer loadTimer;

    
    /**
     * Creates a simple ngram model from the data at the URL. The
     * data should be an ARPA format
     *
     * @param context the context for this model
     *
     * @throws IOException if there is trouble loading the data
     */
    public NioBinaryLoader(String context) 
        throws IOException, FileNotFoundException {
	initialize(context);
    }


    /**
     * Returns the number of unigrams
     *
     * @return the nubmer of unigrams
     */
    public int getNumberUnigrams() {
        return numberUnigrams;
    }


    /**
     * Returns the number of bigrams
     *
     * @return the nubmer of bigrams
     */
    public int getNumberBigrams() {
        return numberBigrams;
    }


    /**
     * Returns the number of trigrams
     *
     * @return the nubmer of trigrams
     */
    public int getNumberTrigrams() {
        return numberTrigrams;
    }


    /**
     * Returns all the unigrams
     *
     * @return all the unigrams
     */
    public UnigramProbability[] getUnigrams() {
        return unigrams;
    }


    /**
     * Returns all the bigram probabilities.
     *
     * @return all the bigram probabilities
     */
    public float[] getBigramProbabilities() {
        return bigramProbTable;
    }


    /**
     * Returns all the trigram probabilities.
     *
     * @return all the trigram probabilities
     */
    public float[] getTrigramProbabilities() {
        return trigramProbTable;
    }


    /**
     * Returns all the trigram backoff weights
     *
     * @return all the trigram backoff weights
     */
    public float[] getTrigramBackoffWeights() {
        return trigramBackoffTable;
    }


    /**
     * Returns the trigram segment table.
     *
     * @return the trigram segment table
     */
    public int[] getTrigramSegments() {
        return trigramSegmentTable;
    }


    /**
     * Returns the log of the bigram segment size
     *
     * @return the log of the bigram segment size
     */
    public int getLogBigramSegmentSize() {
        return logBigramSegmentSize;
    }


    /**
     * Returns all the words.
     *
     * @return all the words
     */
    public String[] getWords() {
        return words;
    }


    /**
     * Initializes this LanguageModel
     *
     * @param context the context to associate this linguist with
     */
    private void initialize(String context) throws IOException {
        this.props = SphinxProperties.getSphinxProperties(context);
        
        String format = props.getString
            (LanguageModel.PROP_FORMAT, LanguageModel.PROP_FORMAT_DEFAULT);
        String location = props.getString
            (LanguageModel.PROP_LOCATION, LanguageModel.PROP_LOCATION_DEFAULT);

        applyLanguageWeightAndWip = props.getBoolean
            (PROP_APPLY_LANGUAGE_WEIGHT_AND_WIP,
             PROP_APPLY_LANGUAGE_WEIGHT_AND_WIP_DEFAULT);

        headerTimer = Timer.getTimer(context, "HeaderRead");
        unigramTimer = Timer.getTimer(context, "UnigramRead");
        uwTimer = Timer.getTimer(context, "ApplyUW");
        tablesTimer = Timer.getTimer(context, "TablesRead");
        stringTimer = Timer.getTimer(context, "StringRead");
        parseTimer = Timer.getTimer(context, "Parse");
        loadTimer = Timer.getTimer(context, "Load");

        logMath = LogMath.getLogMath(context);
        loadBinary(location);
    }
    

    /**
     * Provides the log base that controls the range of probabilities
     * returned by this N-Gram
     */
    public void setLogMath(LogMath logMath) {
        this.logMath = logMath;
    }


    /**
     * Returns the log math the controls the log base for the range of
     * probabilities used by this n-gram
     */
    public LogMath getLogMath() {
        return this.logMath;
    }


    /**
     * Returns the location (or offset) into the file where bigrams start.
     *
     * @return the location of the bigrams
     */
    public int getBigramOffset() {
        return bigramOffset;
    }


    /**
     * Returns the location (or offset) into the file where trigrams start.
     *
     * @return the location of the trigrams
     */
    public int getTrigramOffset() {
        return trigramOffset;
    }


    /**
     * Returns the maximum depth of the language model
     *
     * @return the maximum depth of the language mdoel
     */
    public int getMaxDepth() {
        return maxNGram;
    }


    /**
     * Loads the contents of the memory-mapped file starting at the 
     * given position and for the given size, into a byte buffer.
     * This method is implemented because MappedByteBuffer.load()
     * does not work properly.
     *
     * @param position the starting position in the file
     * @param size the number of bytes to load
     *
     * @return the loaded ByteBuffer
     */
    public ByteBuffer loadBuffer(long position, int size) throws IOException {
        file.seek(position);
        byte[] bytes = new byte[size];
        if (file.read(bytes) != size) {
            throw new IOException("Incorrect number of bytes read.");
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes);
	if (!bigEndian) {
	    bb.order(ByteOrder.LITTLE_ENDIAN);
	} else {
            bb.order(ByteOrder.BIG_ENDIAN);
        }
        return bb;
    }


    /**
     * Loads the bigram at the given absolute index into the bigram region.
     *
     * @param index the absolute index into the bigram region
     *
     * @return a ByteBuffer of the requested bigram
     */
    public BigramBuffer loadBigram(int index) throws IOException {
        long position = (long) bigramOffset + 
            (index * LargeTrigramModel.BYTES_PER_BIGRAM);
        ByteBuffer buffer = loadBuffer
            (position, LargeTrigramModel.BYTES_PER_BIGRAM);
        return (new BigramBuffer(buffer, 1));
    }


    /**
     * Loads the language model from the given location. 
     *
     * @param location the location of the language model
     */
    private void loadBinary(String location) throws IOException {

        FileInputStream fis = new FileInputStream(location);
	FileChannel fileChannel = fis.getChannel();
        MappedByteBuffer bb = fileChannel.map
            (FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());

	// read standard header string-size; set bigEndian flag

        headerTimer.start();
	
	int headerLength = bb.getInt();

	if (headerLength != (DARPA_LM_HEADER.length() + 1)) { // not big-endian
	    headerLength = Utilities.swapInteger(headerLength);

            if (headerLength == (DARPA_LM_HEADER.length() + 1)) {
		// little-endian
                bigEndian = false;
                bb.order(ByteOrder.LITTLE_ENDIAN);
	    } else {
		throw new Error
		    ("Bad binary LM file magic number: " + headerLength +
		     ", not an LM dumpfile?");
	    }
	} else {
            bigEndian = true;
        }

	// read and verify standard header string
        bb.position(bb.position() + headerLength);
        /*
	if (!header.equals(DARPA_LM_HEADER)) {
	    throw new Error("Bad binary LM file header: " + header);
	}
        */

	// read LM filename string size and string
        int fileNameLength = bb.getInt();
        bb.position(bb.position() + fileNameLength);

	numberUnigrams = 0;
	logBigramSegmentSize = LOG2_BIGRAM_SEGMENT_SIZE_DEFAULT;
	
	// read version number, if present. it must be <= 0.

	int version = bb.getInt();
	// System.out.println("Version: " + version);
	
        if (version <= 0) { // yes, its the version number
	    bb.getInt();

	    // read and skip format description
	    int formatLength;
	    for (;;) {
		if ((formatLength = bb.getInt()) == 0) {
		    break;
		}
                bb.position(bb.position() + formatLength);
            }

	    // read log bigram segment size if present
	    if (version <= -2) {
		logBigramSegmentSize = bb.getInt();
		if (logBigramSegmentSize < 1 || logBigramSegmentSize > 15) {
		    throw new Error("log2(bg_seg_sz) outside range 1..15");
		}
	    }

	    numberUnigrams = bb.getInt();
	} else {
	    numberUnigrams = version;
	}

        int bigramSegmentSize = 1 << logBigramSegmentSize;

	if (numberUnigrams <= 0) {
	    throw new Error("Bad number of unigrams: " + numberUnigrams +
			    ", must be > 0.");
	}
	// System.out.println("# of unigrams: " + numberUnigrams);

	if ((numberBigrams = bb.getInt()) < 0) {
	    throw new Error("Bad number of bigrams: " + numberBigrams);
	}
	// System.out.println("# of bigrams: " + numberBigrams);

	if ((numberTrigrams = bb.getInt()) < 0) {
	    throw new Error("Bad number of trigrams: " + numberTrigrams);
	}
	// System.out.println("# of trigrams: " + numberTrigrams);

        headerTimer.stop();

        unigramTimer.start();

	unigrams = readUnigrams(bb, numberUnigrams + 1);

        unigramTimer.stop();

	// skip all the bigram entries, the +1 is the sentinel at the end
	if (numberBigrams > 0) {
            bigramOffset = bb.position();
            int bytesToSkip = (numberBigrams + 1) * 
                LargeTrigramModel.BYTES_PER_BIGRAM;
	    bb.position(bb.position() + bytesToSkip);
	}

	// skip all the trigram entries
	if (numberTrigrams > 0) {
            if ((trigramOffset = bb.position()) < 0) {
                throw new Error("TrigramOffset < 0");
            }
            int bytesToSkip = numberTrigrams * 
                LargeTrigramModel.BYTES_PER_TRIGRAM;
	    bb.position(bb.position() + bytesToSkip);
	}

        tablesTimer.start();

	// read the bigram probabilities table
	if (numberBigrams > 0) {
            this.bigramProbTable = readFloatTable(bb);
	}

	// read the trigram backoff weight table and trigram prob table
	if (numberTrigrams > 0) {
	    trigramBackoffTable = readFloatTable(bb);
	    trigramProbTable = readFloatTable(bb);
            int trigramSegTableSize = ((numberBigrams+1)/bigramSegmentSize)+1;
            trigramSegmentTable = readIntTable(bb, trigramSegTableSize);
        }

        tablesTimer.stop();

	// read word string names
        int wordsStringLength = bb.getInt();
        if (wordsStringLength <= 0) {
            throw new Error("Bad word string size: " + wordsStringLength);
        }

        // read the string of all words
        this.words = readWords(bb, wordsStringLength, numberUnigrams);

        uwTimer.start();

        applyUnigramWeight();

        if (applyLanguageWeightAndWip) {
            applyLanguageWeightAndWip();
        }

        uwTimer.stop();

        file = new RandomAccessFile(location, "r");
    }
    
    
    /**
     * Apply the unigram weight to the set of unigrams
     */
    private void applyUnigramWeight() {

        float unigramWeight = props.getFloat
            (LanguageModel.PROP_UNIGRAM_WEIGHT, 
	     LanguageModel.PROP_UNIGRAM_WEIGHT_DEFAULT);

        float logUnigramWeight = logMath.linearToLog(unigramWeight);
        float logNotUnigramWeight = logMath.linearToLog(1.0f - unigramWeight);
        float logUniform = logMath.linearToLog(1.0f/(numberUnigrams));

        float p2 = logUniform + logNotUnigramWeight;

        for (int i = 0; i < numberUnigrams; i++) {
            if (i != startWordID) {
                float p1 = unigrams[i].getLogProbability() + logUnigramWeight;
                unigrams[i].setLogProbability(logMath.addAsLinear(p1, p2));
            }
        }
    }


    /**
     * Applies the language weight and the word insertion probability to 
     * the probabilities and backoff weights.
     */
    private void applyLanguageWeightAndWip() {

        float languageWeight = 9.5f;
        float wip = logMath.linearToLog(0.7);
        
        // apply to the unigram probabilities
        for (int i = 0; i < numberUnigrams; i++) {
            UnigramProbability unigram = unigrams[i];
            unigram.setLogProbability
                (unigram.getLogProbability() * languageWeight + wip);
            unigram.setLogBackoff(unigram.getLogBackoff() * languageWeight);
        }

        // apply to the bigram probabilities
        for (int i = 0; i < bigramProbTable.length; i++) {
            bigramProbTable[i] = bigramProbTable[i] * languageWeight + wip;
        }

        // apply to the trigram probabilities
        for (int i = 0; i < trigramProbTable.length; i++) {
            trigramProbTable[i] = trigramProbTable[i] * languageWeight + wip;
        }

        // apply to the trigram backoff weights
        for (int i = 0; i < trigramBackoffTable.length; i++) {
            trigramBackoffTable[i] = trigramBackoffTable[i] * languageWeight;
        }
    }

    
    /**
     * Reads the probability table from the given ByteBuffer.
     *
     * @param bb the ByteBuffer from which to read the table
     */
    private float[] readFloatTable(ByteBuffer bb) throws IOException {
        
	int numProbs = bb.getInt();
	if (numProbs <= 0 || numProbs > 65536) {
	    throw new Error("Bad probabilities table size: " + numProbs);
	}
        ByteBuffer current = bb.slice();
        current.order(bb.order());

	float[] probTable = new float[numProbs];
        FloatBuffer fb = current.asFloatBuffer();

        fb.get(probTable);

	for (int i = 0; i < numProbs; i++) {
	    probTable[i] = logMath.log10ToLog(probTable[i]);
	}

        bb.position(bb.position() + numProbs * 4);

	return probTable;
    }


    /**
     * Reads a table of integers from the given ByteBuffer.
     *
     * @param bb the ByteBuffer to read from
     * @param size the size of the trigram segment table
     *
     * @return the trigram segment table, which is an array of integers
     */
    private int[] readIntTable(ByteBuffer bb, int size) throws IOException {
        int numSegments = bb.getInt();
	if (numSegments != size) {
	    throw new Error("Bad trigram seg table size: " + numSegments);
	}
        ByteBuffer current = bb.slice();
        current.order(bb.order());

	int[] segmentTable = new int[numSegments];
        IntBuffer ib = current.asIntBuffer();
        ib.get(segmentTable);

        bb.position(bb.position() + numSegments * 4);
	return segmentTable;
    }


    /**
     * Read in the unigrams in the given ByteBuffer.
     *
     * @param bb the ByteBuffer to read from
     * @param numberUnigrams the number of unigrams to read
     *
     * @return an array of UnigramProbability index by the unigram ID
     */
    private UnigramProbability[] readUnigrams(ByteBuffer bb,
                                              int numberUnigrams)
    throws IOException {

        UnigramProbability[] unigrams = new UnigramProbability[numberUnigrams];
        
	for (int i = 0; i < numberUnigrams; i++) {

	    // read unigram ID, unigram probability, unigram backoff weight
	    int unigramID = bb.getInt();

            // if we're not reading the sentinel unigram at the end,
            // make sure that the unigram IDs are consecutive
            if (i != (numberUnigrams - 1)) {
                assert (unigramID == i);
            }
            
            float unigramProbability = bb.getFloat();
	    float unigramBackoff = bb.getFloat();
	    int firstBigramEntry = bb.getInt();

            float logProbability = logMath.log10ToLog(unigramProbability);
            float logBackoff = logMath.log10ToLog(unigramBackoff);
            
            unigrams[i] = new UnigramProbability
                (unigramID, logProbability, logBackoff, firstBigramEntry);
	}

        return unigrams;
    }


    /**
     * Reads a series of consecutive Strings from the given stream.
     * 
     * @param bb the ByteBuffer to read from
     * @param length the total length in bytes of all the Strings
     * @param numberUnigrams the number of String to read
     *
     * @return an array of the Strings read
     */
    private final String[] readWords(ByteBuffer bb,
                                     int length,
                                     int numberUnigrams)
    throws IOException {
        
        stringTimer.start();
        byte[] bytes = new byte[length];
        bb.get(bytes);
        stringTimer.stop();

        parseTimer.start();

        String[] words = new String[numberUnigrams];
        StringBuffer buffer = new StringBuffer();

        int s = 0;
        for (int i = 0; i < length; i++) {
            char c = (char)bytes[i];
            if (c == '\0') {
                // if its the end of a string, add it to the 'words' array
                words[s] = buffer.toString().toLowerCase();
                buffer = new StringBuffer();
                if (words[s].equals(Dictionary.SENTENCE_START_SPELLING)) {
                    startWordID = s;
                }
                s++;
            } else {
                buffer.append(c);
            }
        }
        assert (s == numberUnigrams);

        parseTimer.stop();

        return words;
    }

}

