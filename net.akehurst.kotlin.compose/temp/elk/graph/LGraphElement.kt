/*******************************************************************************
 * Copyright (c) 2010, 2019 Kiel University and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.elk.alg.layered.graph

import org.eclipse.elk.alg.layered.graph.transform.ElkGraphTransformer
import org.eclipse.elk.graph.properties.MapPropertyHolder

/**
 * Abstract superclass for the layers, nodes, ports, and edges of a layered graph
 * (and the graph itself).
 *
 *
 * The hash code of each graph element is computed from a counter object [HashCodeCounter].
 * Its `count` field is incremented each time a graph element is created. Provided that the
 * same counter object is used for all graph elements, the generated hash codes are unique.
 * The `count` field is not static, because that would cause problems when multiple graphs
 * are processed in parallel. The default hash code inherited from [Object] cannot be used,
 * because it is not deterministic, hence different hash codes would be generated in two consecutive
 * runs on the same graph. As a consequence, hash tables and hash sets would store their content
 * in different order, which can lead to different layouts in some cases. The deterministic hash
 * code implemented here guarantees that such effects will not occur.
 */
abstract class LGraphElement : MapPropertyHolder() {

    /** Identifier value, may be arbitrarily used by algorithms.  */
    var id: Int = 0

    // CHECKSTYLEON VisibilityModifier
    open val designation: String?
        /**
         * Returns a string that is useful to identify the element while debugging.
         *
         * @return the element's designation, or `null` if no meaningful designation can be provided
         */
        get() {
            val identifier: String? = ElkGraphTransformer.getOriginIdentifier(this)
            if (!identifier.isNullOrEmpty()) {
                return identifier
            }
            return null
        }

}