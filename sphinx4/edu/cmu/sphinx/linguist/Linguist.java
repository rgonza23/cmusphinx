
/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

package edu.cmu.sphinx.linguist;

import java.io.IOException;

import edu.cmu.sphinx.util.props.Configurable;

/*
 * The linguist is responsible for representing and managing the search space
 * for the decoder.  The role of the linguist is to provide, upon request,
 * the search graph that is to be used by the decoder.  The linguist is a
 * generic interface that provides language model services. 
 *
 * The lifecycle of a linguist is as follows:
 * <ul>
 * <li> The linguist is created by the configuration manager
 * <li> The linguist is given an opportunity to register its
 * properties via the <code>register</code>
 * <li>  The linguist is give a new set of properties via the
 * <code>newProperties</code> call.  A well written linguist should be
 * prepared to respond to <code>newProperties</code> call a any time
 * <li> The <code>allocate</code> method is called. During this call
 * the linguist generally allocates resources such as acoustic and
 * language models. This can often take a significant amount of time.
 * A well-written linguist will be able to deal with multiple calls to
 * <code>allocate</code>. This can happen if a linguist is shared by
 * multiple search managers.
 * <li> The <code>getSearchGraph</code> method is called by the search
 * to retrieve the search graph that is used to guide the
 * decoding/search.  This method is typically called at the beginning
 * of each recognition. The linguist should endeavor to return the
 * search graph as quickly as possible to reduce any recognition
 * latency.  Some linguists will pre-generate the search graph in the
 * <code>allocate</code> method, and only need to return a reference
 * to the search graph, while other linguists may dynamically generate
 * the search graph on each call.  Also note that some linguists may
 * change the search graph between calls so a search manager should
 * always get a new search graph before the start of each recognition.
 * <li> The <code>startRecognition</code>method is called just before
 * recognition starts. This gives the linguist the opportunity to
 * prepare for the recognition task.  Some linguists may keep caches
 * of search states that need to be primed or flushed. Note however
 * that if a linguist depends on <code>startRecognition</code> or
 * <code>stopRecognition</code> it is likely to not be a reentrant
 * linguist which could limit its usefulness in some multi-threaded
 * environments.
 * <li> The <code>stopRecognition</code>method is called just after
 * recognition completes. This gives the linguist the opportunity to
 * cleanup after the recognition task.  Some linguists may keep caches
 * of search states that need to be primed or flushed. Note however
 * that if a linguist depends on <code>startRecognition</code> or
 * <code>stopRecognition</code> it is likely to not be a reentrant
 * linguist which could limit its usefulness in some multi-threaded
 * environments.
 * </ul>
 */
public interface Linguist extends Configurable {

    // TODO sort out all of these props. Are the all necessary?
    // should the all be here at this level?
    
    /**
      * Word insertion probability property
      */
    public final static String PROP_WORD_INSERTION_PROBABILITY
        = "wordInsertionProbability";


    /**
     * The default value for PROP_WORD_INSERTION_PROBABILITY
     */
    public final static double PROP_WORD_INSERTION_PROBABILITY_DEFAULT = 1.0;


    /**
      * Unit insertion probability property
      */
    public final static String PROP_UNIT_INSERTION_PROBABILITY
        =  "unitInsertionProbability";


    /**
     * The default value for PROP_UNIT_INSERTION_PROBABILITY.
     */
    public final static double PROP_UNIT_INSERTION_PROBABILITY_DEFAULT = 1.0;


    /**
      * Silence insertion probability property
      */
    public final static String PROP_SILENCE_INSERTION_PROBABILITY
        = "silenceInsertionProbability";


    /**
     * The default value for PROP_SILENCE_INSERTION_PROBABILITY.
     */
    public final static double PROP_SILENCE_INSERTION_PROBABILITY_DEFAULT 
        = 1.0;

    /**
      * Filler insertion probability property
      */
    public final static String PROP_FILLER_INSERTION_PROBABILITY
        =  "fillerInsertionProbability";


    /**
     * The default value for PROP_FILLER_INSERTION_PROBABILITY.
     */
    public final static double PROP_FILLER_INSERTION_PROBABILITY_DEFAULT = 1.0;


    /**
     * Sphinx property that defines the language weight for the search
     */
    public final static String PROP_LANGUAGE_WEIGHT  =
	 "languageWeight";


    /**
     * The default value for the PROP_LANGUAGE_WEIGHT property
     */
    public final static float PROP_LANGUAGE_WEIGHT_DEFAULT  = 1.0f;


    /**
     * Property to control the maximum number of right contexts to
     * consider before switching over to using composite hmms
     */
    public final static String PROP_COMPOSITE_THRESHOLD 
        =  "compositeThreshold";

    
    /**
     * The default value for PROP_COMPOSITE_THRESHOLD.
     */
    public final static int PROP_COMPOSITE_THRESHOLD_DEFAULT = 1000;


    /**
     * Property that controls whether word probabilities are spread
     * across all pronunciations.
     */
    public final static 
        String PROP_SPREAD_WORD_PROBABILITIES_ACROSS_PRONUNCIATIONS =
         "spreadWordProbabilitiesAcrossPronunciations";


    /**
     * The default value for 
     * PROP_SPREAD_WORD_PROBABILTIES_ACROSS_PRONUNCIATIONS.
     */
    public final static boolean 
        PROP_SPREAD_WORD_PROBABILITIES_ACROSS_PRONUNCIATIONS_DEFAULT = false;

    /**
     * Property that controls whether filler words are automatically
     * added to the vocabulary
     */
    public final static String PROP_ADD_FILLER_WORDS =
             "addFillerWords";


    /**
     * The default value for PROP_ADD_FILLER_WORDS.
     */
    public final static boolean PROP_ADD_FILLER_WORDS_DEFAULT = false;



    /**
     * Property to control the the dumping of the search space
     */
    public final static String PROP_SHOW_SEARCH_SPACE 
        = "showSearchSpace";


    /**
     * The default value for PROP_SHOW_SEARCH_SPACE.
     */
    public final static boolean PROP_SHOW_SEARCH_SPACE_DEFAULT = false;


    /**
     * Property to control the the validating of the search space
     */
    public final static String PROP_VALIDATE_SEARCH_SPACE
        =  "validateSearchSpace";


    /**
     * The default value for PROP_VALIDATE_SEARCH_SPACE.
     */
    public final static boolean PROP_VALIDATE_SEARCH_SPACE_DEFAULT = false;



    /**
     * Property to control whether compilation progress is displayed
     * on stdout. If this property is true, a 'dot' is displayed for
     * every 1000 search states added to the search space
     */
    public final static String PROP_SHOW_COMPILATION_PROGRESS
        =  "showCompilationProgress";


    /**
     * The default value for PROP_SHOW_COMPILATION_PROGRESS.
     */
    public final static boolean PROP_SHOW_COMPILATION_PROGRESS_DEFAULT = false;


    /**
     * Property to control whether or not the linguist will generate
     * unit states.   When this property is false the linguist may
     * omit UnitSearchState states.  For some search algorithms 
     * this will allow for a faster search with more compact results.
     */
    public final static String PROP_GENERATE_UNIT_STATES
        =  "generateUnitStates";

    /**
     * The default value for PROP_GENERATE_UNIT_STATES
     */
    public final static boolean PROP_GENERATE_UNIT_STATES_DEFAULT = false;

    /**
      * A sphinx property that determines whether or not unigram
      * probabilities are smeared through the lex tree
      */
    public final static String PROP_WANT_UNIGRAM_SMEAR
        = "wantUnigramSmear";

    /**
     * The default value for PROP_WANT_UNIGRAM_SMEAR
     */
    public final static boolean PROP_WANT_UNIGRAM_SMEAR_DEFAULT = false;


    /**
      * A sphinx property that determines the weight of the smear
      */
    public final static String PROP_UNIGRAM_SMEAR_WEIGHT
        = "unigramSmearWeight";

    /**
     * The default value for PROP_UNIGRAM_SMEAR_WEIGHT
     */
    public final static float PROP_UNIGRAM_SMEAR_WEIGHT_DEFAULT = 1.0f;



    /**
     * Retrieves search graph
     * 
     * @return the search graph
     */
    public SearchGraph getSearchGraph();



    /**
     * Called before a recognition
     */
    public void startRecognition();

    /**
     * Called after a recognition
     */
    public void stopRecognition();
    
    
    /**
     * Allocates the linguist
     * @throws IOException if an IO error occurs
     */
    public void allocate() throws IOException;
    
    /**
     * Deallocates the linguist
     *
     */
    public void deallocate();


}

