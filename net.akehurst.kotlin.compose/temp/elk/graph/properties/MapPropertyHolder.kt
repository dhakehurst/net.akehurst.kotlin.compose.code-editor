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
 * An implementation of [IPropertyHolder] based on a [HashMap].
 *
 * @author msp
 */
open class MapPropertyHolder : IPropertyHolder {
    /** map of property identifiers to their values.  */
    private var propertyMap: HashMap<IProperty<*>?, Any?>? = null

    override fun <T> setProperty(property: IProperty<in T?>?, value: T?): MapPropertyHolder {
        if (value == null) {
            this.properties!!.remove(property)
        } else {
            this.properties!!.put(property, value)
        }

        return this
    }

    override fun <T> getProperty(property: IProperty<T?>): T? {
        var value = this.properties!!.get(property)
        if (value is IPropertyValueProxy) {
            value = (value as IPropertyValueProxy).resolveValue(property)
            if (value != null) {
                this.properties!!.put(property, value)
                return value as T
            }
        } else if (value != null) {
            return value as T
        }

        // the reason for the side effect below is that if a default value has been returned
        // and the object is altered by the user, the user expects the altered object to be 
        // the value of the property in case he asks for the property again

        // Retrieve the default value and memorize it for our property
        val defaultValue: T? = property.getDefault()
        if (defaultValue is Cloneable) {
            // We are now dealing with a clone of the default value which me may safely store away
            // for further modification
            setProperty<T?>(property, defaultValue)
        }
        return defaultValue
    }

    public override fun hasProperty(property: IProperty<*>?): Boolean {
        return propertyMap != null && propertyMap!!.containsKey(property)
    }

    public override fun copyProperties(other: IPropertyHolder?): MapPropertyHolder {
        if (other == null) {
            return this
        }

        val otherMap: MutableMap<IProperty<*>?, Any?> = other.getAllProperties()
        if (!otherMap.isEmpty()) {
            if (this.propertyMap == null) {
                propertyMap = HashMap<IProperty<*>?, Any?>(otherMap)
            } else {
                this.propertyMap!!.putAll(otherMap)
            }
        }

        return this
    }

    val allProperties: MutableMap<IProperty<*>, Any?>
        get() {
            if (propertyMap == null) {
                return mutableMapOf<IProperty<*>?, Any?>()
            } else {
                return propertyMap
            }
        }

    private val properties: MutableMap<IProperty<*>, Any?>?
        /**
         * Returns the property map, creating a new map if there hasn't been one so far.
         *
         * @return the property map.
         */
        get() {
            if (propertyMap == null) {
                propertyMap = hashMapOf()
            }
            return propertyMap
        }

}