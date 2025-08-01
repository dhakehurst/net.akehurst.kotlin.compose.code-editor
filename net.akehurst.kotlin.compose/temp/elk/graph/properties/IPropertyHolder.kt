/*******************************************************************************
 * Copyright (c) 2009, 2017 Kiel University and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.elk.graph.properties

/**
 * Interface for holders of property values.
 *
 * @author msp
 */
interface IPropertyHolder {
    /**
     * Sets a property value. No type checking is performed while setting, so
     * users of this method must take care that the right object types are generated.
     *
     * @param <T> type of property
     * @param property the property to set
     * @param value the new value
     * @return `this` [IPropertyHolder] for convenience
    </T> */
    fun <T> setProperty(property: IProperty<in T?>?, value: T?): IPropertyHolder?

    /**
     * Retrieves a property value. If the property is not set, its default value shall be taken,
     * which is taken from the given property instance.
     *
     * @param <T> type of property
     * @param property the property to get
     * @return the current value, or the default value if the property is not set
    </T> */
    fun <T> getProperty(property: IProperty<T?>?): T?

    /**
     * Checks whether a value is configured for the given property. If not, the next call to
     * [.getProperty] will return the property's default value and set the
     * property's value to the default value for this property holder, provided that the
     * default value is not `null`. After that, the property value is set and thus
     * all further calls to [.hasProperty] will return `true` for
     * that property.
     *
     * @param property the property.
     * @return `true` or `false` as a value is or is not set for the property.
     */
    fun hasProperty(property: IProperty<*>?): Boolean

    /**
     * Copy all properties from another property holder to this one.
     *
     * @param holder another property holder
     * @return `this` [IPropertyHolder] for convenience
     */
    fun copyProperties(holder: IPropertyHolder?): IPropertyHolder?

    /**
     * Returns a map of all assigned properties with associated values.
     *
     * @return a map of all properties
     */
    val allProperties: MutableMap<IProperty<*>, Any?>?
}