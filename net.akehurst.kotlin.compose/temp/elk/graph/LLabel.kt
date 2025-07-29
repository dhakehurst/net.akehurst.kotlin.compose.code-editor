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

/**
 * A label in the layered graph structure.
 */
class LLabel(
    /** text of the label.  */
    val text: String? = ""
) : LShape() {

    override fun toString(): String {
        val designation = this.designation
        if (designation == null) {
            return "label"
        } else {
            return "l_" + designation
        }
    }

    override val designation: String?
        get() {
            if (!text.isNullOrEmpty()) {
                return text
            }
            return super.designation
        }

}