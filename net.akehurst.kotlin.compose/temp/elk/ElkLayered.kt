/*******************************************************************************
 * Copyright (c) 2010, 2020 Kiel University and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.elk.alg.layered

import org.eclipse.elk.alg.layered.components.ComponentsProcessor
import org.eclipse.elk.alg.layered.compound.CompoundGraphPostprocessor
import org.eclipse.elk.alg.layered.compound.CompoundGraphPreprocessor
import org.eclipse.elk.alg.layered.graph.LGraph
import org.eclipse.elk.alg.layered.graph.LNode
import org.eclipse.elk.alg.layered.graph.LNode.NodeType
import org.eclipse.elk.alg.layered.graph.LPadding
import org.eclipse.elk.alg.layered.options.CrossingMinimizationStrategy
import org.eclipse.elk.alg.layered.options.GraphProperties
import org.eclipse.elk.alg.layered.options.GreedySwitchType
import org.eclipse.elk.alg.layered.options.InternalProperties
import org.eclipse.elk.alg.layered.options.LayeredOptions
import org.eclipse.elk.core.UnsupportedGraphException
import org.eclipse.elk.core.alg.ILayoutProcessor
import org.eclipse.elk.core.math.KVector
import org.eclipse.elk.core.options.ContentAlignment
import org.eclipse.elk.core.options.PortSide
import org.eclipse.elk.core.options.SizeConstraint
import org.eclipse.elk.core.options.SizeOptions
import org.eclipse.elk.core.testing.TestController
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.core.util.ElkUtil
import org.eclipse.elk.core.util.IElkProgressMonitor
import java.util.Deque

/**
 * The main entry point into ELK Layered. ELK Layered is a layout algorithm after the layered
 * layout method proposed by Sugiyama et al. It is structured into five main phases: cycle breaking,
 * layering, crossing minimization, node placement, and edge routing. Before these phases and after
 * the last phase so called intermediate layout processors can be inserted that do some kind of pre
 * or post processing. Implementations of the different main phases specify the intermediate layout
 * processors they require, which are automatically collected and inserted between the main phases.
 * The layout provider itself also specifies some dependencies.
 *
 * <pre>
 * Intermediate Layout Processors
 * ---------------------------------------------------
 * |         |         |         |         |         |
 * |   ---   |   ---   |   ---   |   ---   |   ---   |
 * |   | |   |   | |   |   | |   |   | |   |   | |   |
 * |   | |   |   | |   |   | |   |   | |   |   | |   |
 * | |       | |       | |       | |       | |
 * | |       | |       | |       | |       | |
 * ---       ---       ---       ---       ---
 * Phase 1   Phase 2   Phase 3   Phase 4   Phase 5
</pre> *
 *
 *
 * To use ELK Layered to layout a given graph, there are three possibilities depending on the kind
 * of graph that is to be laid out:
 *
 *  1. [.doLayout] computes a layout for the given graph, without
 * any subgraphs it might have.
 *  1. [.doCompoundLayout] computes a layout for the given graph
 * and for its subgraphs, if any. (Subgraphs are attached to nodes through the
 * [InternalProperties.NESTED_LGRAPH] property.)
 *  1. If you have an `ElkNode` instead of an `LGraph`, you might want to use
 * [LayeredLayoutProvider.layout]
 * instead.
 *
 *
 * In addition to regular layout runs, this class provides methods for automatic unit testing based
 * around the concept of *test runs*. A test run is executed as follows:
 *
 *  1. Call [.prepareLayoutTest] to start a new run. The given graph might be split
 * into its connected components, which are put into the returned state object.
 *  1. Call one of the actual test methods. [.runLayoutTestStep] runs the
 * next step of the algorithm. [.runLayoutTestUntil] runs the
 * algorithm until a given layout processor has finished executing (its sibling,
 * [.runLayoutTestUntil], can also stop just before a
 * given layout processor starts executing). All of these methods resume execution from where the
 * algorithm has stopped previously.
 *
 *
 * ELK Layered also supports the ELK unit test framework through being a white box testable algorithm.
 *
 * @see ILayoutProcessor
 *
 * @see GraphConfigurator
 *
 * @see IHierarchyAwareLayoutProcessor
 *
 *
 * @author msp
 * @author cds
 */
class ElkLayered {
    /**///////////////////////////////////////////////////////////////////////////// */ // Variables
    /** the algorithm's current configuration.  */
    private val graphConfigurator: GraphConfigurator = GraphConfigurator()

    /** connected components processor.  */
    private val componentsProcessor: ComponentsProcessor = ComponentsProcessor()

    /** compound graph preprocessor.  */
    private val compoundGraphPreprocessor: CompoundGraphPreprocessor = CompoundGraphPreprocessor()

    /** compound graph postprocessor.  */
    private val compoundGraphPostprocessor: CompoundGraphPostprocessor = CompoundGraphPostprocessor()

    /** Test controller for a white box test.  */
    private var testController: TestController? = null


    /**///////////////////////////////////////////////////////////////////////////// */ // Regular Layout
    /**
     * Does a layout on the given graph. If the graph contains compound nodes (see class documentation),
     * the nested graphs are ignored.
     *
     * @param lgraph the graph to layout
     * @param monitor a progress monitor to show progress information in, or `null`
     * @see .doCompoundLayout
     */
    fun doLayout(lgraph: LGraph, monitor: IElkProgressMonitor) {
        var theMonitor: IElkProgressMonitor? = monitor
        if (theMonitor == null) {
            theMonitor = BasicProgressMonitor().withMaxHierarchyLevels(0)
        }
        theMonitor.begin("Layered layout", 1)

        // Update the modules depending on user options
        graphConfigurator.prepareGraphForLayout(lgraph)

        // Split the input graph into components and perform layout on them
        val components: MutableList<LGraph> = componentsProcessor.split(lgraph)
        if (components.size == 1) {
            // Execute layout on the sole component using the top-level progress monitor
            layout(components.get(0), theMonitor)
        } else {
            // Execute layout on each component using a progress monitor subtask
            val compWork = 1.0f / components.size
            for (comp in components) {
                if (monitor.isCanceled()) {
                    return
                }
                layout(comp, theMonitor.subTask(compWork))
            }
        }
        componentsProcessor.combine(components, lgraph)

        // Resize the resulting graph, according to minimal size constraints and such
        resizeGraph(lgraph)

        theMonitor.done()
    }


    /**///////////////////////////////////////////////////////////////////////////// */ // Compound Graph Layout
    /**
     * Does a layout on the given compound graph. Connected components processing is currently not
     * supported.
     *
     * @param lgraph the graph to layout
     * @param monitor a progress monitor to show progress information in, or `null`
     */
    fun doCompoundLayout(lgraph: LGraph, monitor: IElkProgressMonitor?) {
        var theMonitor: IElkProgressMonitor? = monitor
        if (theMonitor == null) {
            theMonitor = BasicProgressMonitor().withMaxHierarchyLevels(0)
        }
        theMonitor.begin("Layered layout", 2) // SUPPRESS CHECKSTYLE MagicNumber

        // Preprocess the compound graph by splitting cross-hierarchy edges
        notifyProcessorReady(lgraph, compoundGraphPreprocessor)
        compoundGraphPreprocessor.process(lgraph, theMonitor.subTask(1))
        notifyProcessorFinished(lgraph, compoundGraphPreprocessor)

        hierarchicalLayout(lgraph, theMonitor.subTask(1))

        // Postprocess the compound graph by combining split cross-hierarchy edges
        notifyProcessorReady(lgraph, compoundGraphPostprocessor)
        compoundGraphPostprocessor.process(lgraph, theMonitor.subTask(1))
        notifyProcessorFinished(lgraph, compoundGraphPostprocessor)

        theMonitor.done()
    }

    /**
     * Processors can be marked as operating on the full hierarchy by using the [IHierarchyAwareLayoutProcessor]
     * interface.
     *
     * All graphs are collected using a breadth-first search and this list is reversed, so that for each graph, all
     * following graphs are on the same hierarchy level or higher, i.e. closer to the parent graph. Each graph then has
     * a unique configuration of ELK Layered, which is comprised of a sequence of processors. The processors can vary
     * depending on the characteristics of each graph. The list of graphs and their algorithms is then traversed. If a
     * processor is not hierarchical it is simply executed. If it it is hierarchical and this graph is not the root
     * graph, this processor is skipped and the algorithm is paused until the processor has been executed on the root
     * graph. Then the algorithm is continued, starting with the level lowest in the hierarchy, i.e. furthest away from
     * the root graph.
     */
    private fun hierarchicalLayout(lgraph: LGraph, monitor: IElkProgressMonitor) {
        // Perform a reversed breadth first search: The graphs in the lowest hierarchy come first.
        val graphs: MutableCollection<LGraph> = collectAllGraphsBottomUp(lgraph)

        // We have to make sure that hierarchical processors don't break the control flow
        //  of the following layout execution (see e.g. #228). To be precise, if the root node of
        //  the hierarchical graph doesn't include a hierarchical processor, nor may any of the children.
        reviewAndCorrectHierarchicalProcessors(lgraph, graphs)


        // Get list of processors for each graph, since they can be different.
        // Iterators are used, so that processing of a graph can be paused and continued easily.
        var work = 0
        val graphsAndAlgorithms: MutableList<Pair<LGraph?, MutableIterator<ILayoutProcessor<LGraph?>?>?>> = java.util.ArrayList<Pair<LGraph?, MutableIterator<ILayoutProcessor<LGraph?>?>?>>()
        for (g in graphs) {
            graphConfigurator.prepareGraphForLayout(g)
            val processors: MutableList<ILayoutProcessor<LGraph?>?> = g.getProperty(InternalProperties.PROCESSORS)
            work += processors.size
            val algorithm: MutableIterator<ILayoutProcessor<LGraph?>?> = processors.iterator()
            graphsAndAlgorithms.add(Pair.of(g, algorithm))
        }

        monitor.begin("Recursive hierarchical layout", work)

        // When the root graph has finished layout, the layout is complete.
        var slotIndex = 0
        val rootProcessors: MutableIterator<ILayoutProcessor<LGraph?>?> = getProcessorsForRootGraph(graphsAndAlgorithms)
        while (rootProcessors.hasNext()) {
            // Layout from bottom up
            for (graphAndAlgorithm in graphsAndAlgorithms) {
                val processors: MutableIterator<ILayoutProcessor<LGraph?>> = graphAndAlgorithm.getSecond()
                val graph: LGraph = graphAndAlgorithm.getFirst()

                while (processors.hasNext()) {
                    val processor: ILayoutProcessor<LGraph?> = processors.next()
                    if (processor !is IHierarchyAwareLayoutProcessor) {
                        // Output debug graph
                        // elkjs-exclude-start
                        if (monitor.isLoggingEnabled()) {
                            DebugUtil.logDebugGraph(
                                monitor, graph, slotIndex,
                                "Before " + processor.getClass().getSimpleName()
                            )
                        }

                        // elkjs-exclude-end
                        notifyProcessorReady(graph, processor)
                        processor.process(graph, monitor.subTask(1))
                        notifyProcessorFinished(graph, processor)

                        slotIndex++
                    } else if (isRoot(graph)) {
                        // Output debug graph
                        // elkjs-exclude-start
                        if (monitor.isLoggingEnabled()) {
                            DebugUtil.logDebugGraph(
                                monitor, graph, slotIndex,
                                "Before " + processor.getClass().getSimpleName()
                            )
                        }

                        // elkjs-exclude-end

                        // If processor operates on the full hierarchy, it must be executed on the root
                        notifyProcessorReady(graph, processor)
                        processor.process(graph, monitor.subTask(1))
                        notifyProcessorFinished(graph, processor)

                        slotIndex++


                        // Continue operation with the graph at the bottom of the hierarchy
                        break
                    } else { // operates on full hierarchy and is not root graph
                        // skip this processor and pause execution until root graph has processed.
                        break
                    }
                }
            }
        }

        // Graph debug output
        // elkjs-exclude-start
        if (monitor.isLoggingEnabled()) {
            DebugUtil.logDebugGraph(monitor, lgraph, slotIndex, "Finished")
        }

        // elkjs-exclude-end
        monitor.done()
    }

    /**
     * Implements a breadth first search in compound graphs with reversed order. This way the
     * innermost graphs are first in the list, followed by one level further up, etc.
     *
     * @param root
     * the root graph
     * @return Graphs in breadth first search in compound graphs in reverse order.
     */
    private fun collectAllGraphsBottomUp(root: LGraph?): MutableCollection<LGraph> {
        val collectedGraphs: Deque<LGraph> = java.util.ArrayDeque<LGraph>()
        val continueSearchingTheseGraphs: Deque<LGraph> = java.util.ArrayDeque<LGraph>()
        collectedGraphs.push(root)
        continueSearchingTheseGraphs.push(root)

        while (!continueSearchingTheseGraphs.isEmpty()) {
            val nextGraph: LGraph = continueSearchingTheseGraphs.pop()
            for (node in nextGraph.getLayerlessNodes()) {
                if (hasNestedGraph(node)) {
                    val nestedGraph: LGraph? = node.getNestedGraph()
                    collectedGraphs.push(nestedGraph)
                    continueSearchingTheseGraphs.push(nestedGraph)
                }
            }
        }
        return collectedGraphs
    }

    /**
     * It is not permitted that any of the child-graphs specifies a hierarchical
     * layout processor ([IHierarchyAwareLayoutProcessor]) that is not specified by the root node.
     *
     * It depends on the concrete processor how this is fixed.
     *
     * @param root the root graph
     * @param graphs all graphs of the handled hierarchy
     */
    private fun reviewAndCorrectHierarchicalProcessors(root: LGraph, graphs: MutableCollection<LGraph>) {
        // Crossing minimization
        //  overwrite invalid child configuration (only layer sweep is hierarchical)
        val parentCms: CrossingMinimizationStrategy? = root.getProperty(LayeredOptions.CROSSING_MINIMIZATION_STRATEGY)
        graphs.forEach(java.util.function.Consumer { child: LGraph ->
            val childCms: CrossingMinimizationStrategy? =
                child.getProperty(LayeredOptions.CROSSING_MINIMIZATION_STRATEGY)
            if (childCms !== parentCms) {
                throw UnsupportedGraphException(
                    ("The hierarchy aware processor " + childCms + " in child node "
                            + child + " is only allowed if the root node specifies the same hierarchical processor.")
                )
            }
        })


        // Greedy switch (simply copy the behavior of the root to all children)
        val rootType: GreedySwitchType? =
            root.getProperty(LayeredOptions.CROSSING_MINIMIZATION_GREEDY_SWITCH_HIERARCHICAL_TYPE)
        graphs.forEach(
            java.util.function.Consumer { g: LGraph -> g.setProperty(LayeredOptions.CROSSING_MINIMIZATION_GREEDY_SWITCH_HIERARCHICAL_TYPE, rootType) })
    }

    private fun getProcessorsForRootGraph(
        graphsAndAlgorithms: MutableList<Pair<LGraph?, MutableIterator<ILayoutProcessor<LGraph?>?>?>>
    ): MutableIterator<ILayoutProcessor<LGraph?>?> {
        return graphsAndAlgorithms.get(graphsAndAlgorithms.size - 1).getSecond()
    }

    private fun isRoot(graph: LGraph): Boolean {
        return graph.getParentNode() == null
    }

    private fun hasNestedGraph(node: LNode): Boolean {
        return node.getNestedGraph() != null
    }

    /**///////////////////////////////////////////////////////////////////////////// */ // Layout Testing
    /**
     * The state of a test execution is held in an instance of this class.
     */
    class TestExecutionState {
        /**
         * Return the list of graphs that are currently being laid out.
         *
         * @return the graphs under test
         */
        /** list of graphs that are currently being laid out.  */
        var graphs: MutableList<LGraph>? = null
            private set
        /**
         * Return the index of the processor that is to be executed next during a layout test.
         *
         * @return the index of the next step
         */
        /** index of the processor that is to be executed next during a layout test.  */
        var step: Int = 0
            private set
    }

    /**
     * Prepares a test run of the layout algorithm. After this method has run, call
     * [.layoutTestStep] as often as there are layout processors.
     *
     * @param lgraph the input graph to initialize the test run with.
     * @return the test execution state
     */
    fun prepareLayoutTest(lgraph: LGraph?): TestExecutionState {
        val state = TestExecutionState()

        // update the modules depending on user options
        graphConfigurator.prepareGraphForLayout(lgraph)

        // split the input graph into components
        state.graphs = componentsProcessor.split(lgraph)

        return state
    }

    /**
     * Checks if the current test run still has processors to be executed for the algorithm to finish.
     *
     * @param state the current test execution state
     * @return `true` if the current test run has not finished yet. If there is no current
     * test run, the result is undefined.
     */
    fun isLayoutTestFinished(state: TestExecutionState): Boolean {
        val graph: LGraph = state.graphs!!.get(0)
        val algorithm: MutableList<ILayoutProcessor<LGraph?>?>? = graph.getProperty(InternalProperties.PROCESSORS)
        return algorithm != null && state.step >= algorithm.size
    }

    /**
     * Runs the algorithm on the current test graphs up to the point where the given phase or
     * processor has finished executing. If parts of the algorithm were already executed using this
     * or other layout test methods, execution is resumed from there. If the given phase or
     * processor is not among those processors that have not yet executed, an exception is thrown.
     * Also, if there is no current layout test run, an exception is thrown.
     *
     * @param phase the phase or processor to stop after
     * @param inclusive `true` if the specified phase should be executed as well
     * @param state the current test execution state
     * @throws IllegalArgumentException
     * if the given layout processor is not part of the processors that are still to be
     * executed.
     */
    fun runLayoutTestUntil(
        phase: java.lang.Class<out ILayoutProcessor<LGraph?>?>?,
        inclusive: Boolean, state: TestExecutionState
    ) {
        val algorithm: MutableList<ILayoutProcessor<LGraph?>?> = state.graphs!!.get(0).getProperty(InternalProperties.PROCESSORS)

        // check if the given phase exists in our current algorithm configuration
        var phaseExists = false
        var algorithmIterator: MutableListIterator<ILayoutProcessor<LGraph?>?> = algorithm.listIterator(state.step)
        var phaseIndex = state.step

        while (algorithmIterator.hasNext() && !phaseExists) {
            if (algorithmIterator.next().getClass().equals(phase)) {
                phaseExists = true

                if (inclusive) {
                    phaseIndex++
                }
            } else {
                phaseIndex++
            }
        }

        if (!phaseExists) {
            // FIXME actually, we want to know when a processor is not
            //  part of the algorithm's configuration because this might be
            //  wrong behavior.
            // However, in the current test framework there is no way
            //  to differentiate between 'it's ok' and 'it's not'.
            // throw new IllegalArgumentException(
            // "Given processor not part of the remaining algorithm.");
            java.lang.System.err.println("Given processor " + phase + " not part of the remaining algorithm.")
        }

        // perform the layout up to and including that phase
        algorithmIterator = algorithm.listIterator(state.step)
        while (state.step < phaseIndex) {
            layoutTest(state.graphs, algorithmIterator.next())
            state.step++
        }
    }

    /**
     * Performs the [.runLayoutTestUntil] methods with `inclusive` set
     * to `true`.
     *
     * @param phase the phase or processor to stop after
     * @param state the current test execution state
     * @see ElkLayered.runLayoutTestUntil
     */
    fun runLayoutTestUntil(
        phase: java.lang.Class<out ILayoutProcessor<LGraph?>?>?,
        state: TestExecutionState
    ) {
        runLayoutTestUntil(phase, true, state)
    }

    /**
     * Runs the next step of the current layout test run. Throws exceptions if no layout test run is
     * currently active or if the current run has finished.
     *
     * @param state the current test execution state
     * @throws IllegalStateException if the given state has finished executing
     */
    fun runLayoutTestStep(state: TestExecutionState) {
        check(!isLayoutTestFinished(state)) { "Current layout test run has finished." }

        // perform the next layout step
        val algorithm: MutableList<ILayoutProcessor<LGraph?>?> = state.graphs!!.get(0).getProperty(InternalProperties.PROCESSORS)
        layoutTest(state.graphs, algorithm.get(state.step))
        state.step++
    }

    /**
     * Returns the current list of layout processors that make up the algorithm. This list is only
     * valid and meaningful while a layout test is being run.
     *
     * @param state the current test execution state
     * @return the algorithm's current configuration.
     */
    fun getLayoutTestConfiguration(state: TestExecutionState): MutableList<ILayoutProcessor<LGraph?>?> {
        return state.graphs!!.get(0).getProperty(InternalProperties.PROCESSORS)
    }

    /**
     * Installs the given test controller to be notified of layout events.
     *
     * @param testController the test controller to be installed. May be `null`.
     */
    fun setTestController(testController: TestController?) {
        this.testController = testController
    }

    /**
     * Notifies the test controller (if installed) that the given processor is ready to start processing the given
     * graph. If the graph is the root graph, the corresponding notification is triggered as well.
     */
    private fun notifyProcessorReady(lgraph: LGraph, processor: ILayoutProcessor<*>?) {
        if (testController != null) {
            if (isRoot(lgraph)) {
                testController.notifyRootProcessorReady(lgraph, processor)
            } else {
                testController.notifyProcessorReady(lgraph, processor)
            }
        }
    }

    /**
     * Notifies the test controller (if installed) that the given processor has finished processing the given
     * graph. If the graph is the root graph, the corresponding notification is triggered as well.
     */
    private fun notifyProcessorFinished(lgraph: LGraph, processor: ILayoutProcessor<*>?) {
        if (testController != null) {
            if (isRoot(lgraph)) {
                testController.notifyRootProcessorFinished(lgraph, processor)
            } else {
                testController.notifyProcessorFinished(lgraph, processor)
            }
        }
    }


    /**///////////////////////////////////////////////////////////////////////////// */ // Actual Layout
    /**
     * Perform the five phases of the layered layouter.
     *
     * @param lgraph the graph that is to be laid out
     * @param monitor a progress monitor
     */
    private fun layout(lgraph: LGraph, monitor: IElkProgressMonitor) {
        val monitorWasAlreadyRunning: Boolean = monitor.isRunning()
        if (!monitorWasAlreadyRunning) {
            monitor.begin("Component Layout", 1)
        }

        val algorithm: MutableList<ILayoutProcessor<LGraph?>> = lgraph.getProperty(InternalProperties.PROCESSORS)
        val monitorProgress = 1.0f / algorithm.size

        if (monitor.isLoggingEnabled()) {
            // Print the algorithm configuration
            monitor.log("ELK Layered uses the following " + algorithm.size + " modules:")
            var slot = 0
            for (processor in algorithm) {
                // SUPPRESS CHECKSTYLE NEXT MagicNumber
                val gwtDoesntSupportPrintf = (if (slot < 10) "0" else "") + (slot++)
                monitor.log("   Slot " + gwtDoesntSupportPrintf + ": " + processor.getClass().getName())
            }
        }


        // Invoke each layout processor
        var slotIndex = 0
        for (processor in algorithm) {
            if (monitor.isCanceled()) {
                return
            }


            // Output debug graph
            // elkjs-exclude-start
            if (monitor.isLoggingEnabled()) {
                DebugUtil.logDebugGraph(monitor, lgraph, slotIndex, "Before " + processor.getClass().getSimpleName())
            }

            // elkjs-exclude-end
            notifyProcessorReady(lgraph, processor)
            processor.process(lgraph, monitor.subTask(monitorProgress))
            notifyProcessorFinished(lgraph, processor)

            slotIndex++
        }

        // Graph debug output
        // elkjs-exclude-start
        if (monitor.isLoggingEnabled()) {
            DebugUtil.logDebugGraph(monitor, lgraph, slotIndex, "Finished")
        }

        // elkjs-exclude-end

        // Move all nodes away from the layers (we need to remove nodes from their current layer in a
        // second loop to avoid ConcurrentModificationExceptions)
        for (layer in lgraph) {
            lgraph.getLayerlessNodes().addAll(layer.getNodes())
            layer.getNodes().clear()
        }

        for (node in lgraph.getLayerlessNodes()) {
            node.setLayer(null)
        }

        lgraph.getLayers().clear()

        if (!monitorWasAlreadyRunning) {
            monitor.done()
        }
    }

    /**
     * Executes the given layout processor on the given list of graphs.
     *
     * @param lgraphs the list of graphs to be laid out.
     * @param monitor a progress monitor.
     * @param processor processor to execute.
     */
    private fun layoutTest(lgraphs: MutableList<LGraph>, processor: ILayoutProcessor<LGraph?>) {
        // invoke the layout processor on each of the given graphs
        for (lgraph in lgraphs) {
            notifyProcessorReady(lgraph, processor)
            processor.process(lgraph, BasicProgressMonitor())
            notifyProcessorFinished(lgraph, processor)
        }
    }


    /**///////////////////////////////////////////////////////////////////////////// */ // Graph Postprocessing (Size and External Ports)
    /**
     * Sets the size of the given graph such that size constraints are adhered to.
     * Furthermore, the border spacing is added to the graph size and the graph offset.
     * Afterwards, the border spacing property is reset to 0.
     *
     *
     * Major parts of this method are adapted from
     * [ElkUtil.resizeNode].
     *
     *
     * Note: This method doesn't care about labels of compound nodes since those labels are not
     * attached to the graph.
     *
     * @param lgraph the graph to resize.
     */
    private fun resizeGraph(lgraph: LGraph) {
        val sizeConstraint: MutableSet<SizeConstraint?> = lgraph.getProperty(LayeredOptions.NODE_SIZE_CONSTRAINTS)
        val sizeOptions: MutableSet<SizeOptions?> = lgraph.getProperty(LayeredOptions.NODE_SIZE_OPTIONS)

        val calculatedSize: KVector = lgraph.getActualSize()
        val adjustedSize: KVector = KVector(calculatedSize)

        // calculate the new size
        if (sizeConstraint.contains(SizeConstraint.MINIMUM_SIZE)) {
            val minSize: KVector = lgraph.getProperty(LayeredOptions.NODE_SIZE_MINIMUM)

            // if minimum width or height are not set, maybe default to default values
            if (sizeOptions.contains(SizeOptions.DEFAULT_MINIMUM_SIZE)) {
                if (minSize.x <= 0) {
                    minSize.x = ElkUtil.DEFAULT_MIN_WIDTH
                }

                if (minSize.y <= 0) {
                    minSize.y = ElkUtil.DEFAULT_MIN_HEIGHT
                }
            }

            // apply new size including border spacing
            adjustedSize.x = java.lang.Math.max(calculatedSize.x, minSize.x)
            adjustedSize.y = java.lang.Math.max(calculatedSize.y, minSize.y)
        }

        if (!lgraph.getProperty(LayeredOptions.NODE_SIZE_FIXED_GRAPH_SIZE)) {
            resizeGraphNoReallyIMeanIt(lgraph, calculatedSize, adjustedSize)
        }
    }


    /**
     * Applies a new effective size to a graph that previously had an old size calculated by the
     * layout algorithm. This method takes care of adjusting content alignments as well as external
     * ports that would be misplaced if the new size is larger than the old one.
     *
     * @param lgraph
     * the graph to apply the size to.
     * @param oldSize
     * old size as calculated by the layout algorithm.
     * @param newSize
     * new size that may be larger than the old one.
     */
    private fun resizeGraphNoReallyIMeanIt(
        lgraph: LGraph, oldSize: KVector,
        newSize: KVector
    ) {
        // obey to specified alignment constraints

        val contentAlignment: MutableSet<ContentAlignment?> =
            lgraph.getProperty(LayeredOptions.CONTENT_ALIGNMENT)

        // horizontal alignment
        if (newSize.x > oldSize.x) {
            if (contentAlignment.contains(ContentAlignment.H_CENTER)) {
                lgraph.getOffset().x += (newSize.x - oldSize.x) / 2f
            } else if (contentAlignment.contains(ContentAlignment.H_RIGHT)) {
                lgraph.getOffset().x += newSize.x - oldSize.x
            }
        }

        // vertical alignment
        if (newSize.y > oldSize.y) {
            if (contentAlignment.contains(ContentAlignment.V_CENTER)) {
                lgraph.getOffset().y += (newSize.y - oldSize.y) / 2f
            } else if (contentAlignment.contains(ContentAlignment.V_BOTTOM)) {
                lgraph.getOffset().y += newSize.y - oldSize.y
            }
        }

        // correct the position of eastern and southern hierarchical ports, if necessary
        if (lgraph.getProperty(InternalProperties.GRAPH_PROPERTIES).contains(
                GraphProperties.EXTERNAL_PORTS
            )
            && (newSize.x > oldSize.x || newSize.y > oldSize.y)
        ) {
            // iterate over the graph's nodes, looking for eastern / southern external ports
            // (at this point, the graph's nodes are not divided into layers anymore)

            for (node in lgraph.getLayerlessNodes()) {
                // we're only looking for external port dummies
                if (node.getType() === NodeType.EXTERNAL_PORT) {
                    // check which side the external port is on
                    val extPortSide: PortSide? = node.getProperty(InternalProperties.EXT_PORT_SIDE)
                    if (extPortSide === PortSide.EAST) {
                        node.getPosition().x += newSize.x - oldSize.x
                    } else if (extPortSide === PortSide.SOUTH) {
                        node.getPosition().y += newSize.y - oldSize.y
                    }
                }
            }
        }

        // Actually apply the new size
        val lPadding: LPadding = lgraph.getPadding()
        lgraph.getSize().x = newSize.x - lPadding.left - lPadding.right
        lgraph.getSize().y = newSize.y - lPadding.top - lPadding.bottom
    }
}