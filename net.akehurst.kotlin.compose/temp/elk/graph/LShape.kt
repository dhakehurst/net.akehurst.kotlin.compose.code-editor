/*******************************************************************************
 * Copyright (c) 2011, 2019 Kiel University and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.elk.alg.layered.graph

import org.eclipse.elk.core.math.KVector

/**
 * Abstract superclass for [LGraphElement]s that can have a position and a size.
 */
abstract class LShape : LGraphElement() {
    /** the size of the element.  */
    val size: KVector = KVector()

    /** the current position of the element.  */
    val position: KVector= KVector()
}