/*******************************************************************************
 * Copyright (c) 2009, 2015 Kiel University and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.elk.graph.properties

/**
 * Interface for property identifiers. Properties have a type and a default value, and
 * they have an internal mechanism for identification, which should be compatible
 * with their [java.lang.Object.equals] and [java.lang.Object.hashCode]
 * implementations.
 *
 * @param <T> type of the property
 * @author msp
</T> */
interface IProperty<T> {
    /**
     * Returns the default value of this property. If the default value implements [Cloneable], this method
     * should indeed return a clone to allow clients to modify it without messing with the original default.
     *
     * @return the default value, or `null` if the property has no default value
     */
    val default: T?

    /**
     * Returns an identifier string for this property.
     *
     * @return an identifier
     */
    val id: String?

    /**
     * Returns the lower bound of this property. If there is no lower bound, a
     * comparable is returned that is smaller than everything else.
     *
     * @return the lower bound
     */
    val lowerBound: Comparable<in T?>?

    /**
     * Returns the upper bound of this property. If there is no upper bound, a
     * comparable is returned that is greater than everything else.
     *
     * @return the upper bound
     */
    val upperBound: Comparable<in T?>?
}