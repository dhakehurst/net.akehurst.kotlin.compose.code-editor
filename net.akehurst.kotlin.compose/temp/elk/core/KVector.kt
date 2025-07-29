/*******************************************************************************
 * Copyright (c) 2010, 2015 Kiel University and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.elk.core.math

data class KVector(
    val x: Double = 0.0,
    val y: Double = 0.0,
)

/*

/**
 * A simple 2D vector class which supports translation, scaling, normalization etc.
 *
 * @author uru
 * @author owo
 * @author cds
 */
class KVector : IDataObject, Cloneable {
    // CHECKSTYLEOFF VisibilityModifier
    /** x coordinate.  */
    var x: Double

    /** y coordinate.  */
    var y: Double

    // CHECKSTYLEON VisibilityModifier
    /**
     * Create vector with default coordinates (0,0).
     */
    constructor() {
        this.x = 0.0
        this.y = 0.0
    }

    /**
     * Constructs a new vector from given values.
     *
     * @param thex
     * x value
     * @param they
     * y value
     */
    constructor(thex: Double, they: Double) {
        this.x = thex
        this.y = they
    }

    /**
     * Creates an exact copy of a given vector v.
     *
     * @param v
     * existing vector
     */
    constructor(v: KVector) {
        this.x = v.x
        this.y = v.y
    }

    /**
     * Creates a new vector that points from the given start to the end vector.
     *
     * @param start
     * start vector.
     * @param end
     * end vector.
     */
    constructor(start: KVector, end: KVector) {
        this.x = end.x - start.x
        this.y = end.y - start.y
    }

    /**
     * Creates a normalized vector for the passed angle in radians.
     *
     * @param angle
     * angle in radians.
     */
    constructor(angle: Double) {
        this.x = cos(angle)
        this.y = sin(angle)
    }

    /**
     * Returns an exact copy of this vector.
     *
     * @return identical vector
     */
    // elkjs-exclude-start
    // elkjs-exclude-end
    public override fun clone(): KVector {
        return KVector(x, y)
    }

    override fun toString(): String {
        return "(" + x + "," + y + ")"
    }

    override fun equals(obj: Any?): Boolean {
        if (obj is KVector) {
            val other = obj
            return this.x == other.x && this.y == other.y
        } else {
            return false
        }
    }

    /**
     * Compares if this and the given vector are approximately equal. What *approximately*
     * means is defined by the fuzzyness: for both x and y coordinate, the two vectors may only
     * differ by at most the fuzzyness to still be considered equal.
     *
     * @param other
     * the vector to compare this vector to.
     * @param fuzzyness
     * the maximum difference per dimension that is still considered equal.
     * @return `true` if the vectors are approximately equal, `false` otherwise.
     */
    /**
     * Calls [.equalsFuzzily] with a default fuzzyness.
     *
     * @param other
     * the vector to compare this vector to.
     * @return `true` if the vectors are approximately equal, `false` otherwise.
     */
    @JvmOverloads
    fun equalsFuzzily(other: KVector, fuzzyness: Double = DEFAULT_FUZZYNESS): Boolean {
        return abs(this.x - other.x) <= fuzzyness
                && abs(this.y - other.y) <= fuzzyness
    }

    override fun hashCode(): Int {
        return x.hashCode() + Int.reverse(y.rev hashCode())
    }

    /**
     * returns this vector's length.
     *
     * @return Math.sqrt(x*x + y*y)
     */
    fun length(): Double {
        return sqrt(x * x + y * y)
    }

    /**
     * returns square length of this vector.
     *
     * @return x*x + y*y
     */
    fun squareLength(): Double {
        return x * x + y * y
    }

    /**
     * Set vector to (0,0).
     *
     * @return `this`
     */
    fun reset(): KVector {
        this.x = 0.0
        this.y = 0.0
        return this
    }

    /**
     * Resets this vector to the value of the other vector.
     *
     * @param other the vector whose values to copy.
     * @return `this`
     */
    fun set(other: KVector): KVector {
        this.x = other.x
        this.y = other.y
        return this
    }

    /**
     * Resets this vector to the given values.
     *
     * @param newX new x value.
     * @param newY new y value.
     * @return `this`
     */
    fun set(newX: Double, newY: Double): KVector {
        this.x = newX
        this.y = newY
        return this
    }

    /**
     * Vector addition.
     *
     * @param v
     * vector to add
     * @return `this + v`
     */
    fun add(v: KVector): KVector {
        this.x += v.x
        this.y += v.y
        return this
    }

    /**
     * Translate the vector by adding the given amount.
     *
     * @param dx
     * the x offset
     * @param dy
     * the y offset
     * @return `this`
     */
    fun add(dx: Double, dy: Double): KVector {
        this.x += dx
        this.y += dy
        return this
    }

    /**
     * Vector subtraction.
     *
     * @param v
     * vector to subtract
     * @return `this`
     */
    fun sub(v: KVector): KVector {
        this.x -= v.x
        this.y -= v.y
        return this
    }

    /**
     * Translate the vector by subtracting the given amount.
     *
     * @param dx
     * the x offset
     * @param dy
     * the y offset
     * @return `this`
     */
    fun sub(dx: Double, dy: Double): KVector {
        this.x -= dx
        this.y -= dy
        return this
    }

    /**
     * Scale the vector.
     *
     * @param scale
     * scaling factor
     * @return `this`
     */
    fun scale(scale: Double): KVector {
        this.x *= scale
        this.y *= scale
        return this
    }

    /**
     * Scale the vector with different values for X and Y coordinate.
     *
     * @param scalex
     * the x scaling factor
     * @param scaley
     * the y scaling factor
     * @return `this`
     */
    fun scale(scalex: Double, scaley: Double): KVector {
        this.x *= scalex
        this.y *= scaley
        return this
    }

    /**
     * Normalize the vector.
     *
     * @return `this`
     */
    fun normalize(): KVector {
        val length = this.length()
        if (length > 0) {
            this.x /= length
            this.y /= length
        }
        return this
    }

    /**
     * scales this vector to the passed length.
     *
     * @param length
     * length to scale to
     * @return `this`
     */
    fun scaleToLength(length: Double): KVector {
        this.normalize()
        this.scale(length)
        return this
    }

    /**
     * Negate the vector.
     *
     * @return `this`
     */
    fun negate(): KVector {
        this.x = -this.x
        this.y = -this.y
        return this
    }

    /**
     * Returns angle representation of this vector in degree. The length of the vector must not be 0.
     *
     * @return value within [0,360)
     */
    fun toDegrees(): Double {
        return java.lang.Math.toDegrees(toRadians())
    }

    /**
     * Returns angle representation of this vector in radians. The length of the vector must not be 0.
     *
     * @return value within [0,2*pi)
     */
    fun toRadians(): Double {
        val length = this.length()
        assert(length > 0)

        if (x >= 0 && y >= 0) {  // 1st quadrant
            return asin(y / length)
        } else if (x < 0) {      // 2nd or 3rd quadrant
            return java.lang.Math.PI - asin(y / length)
        } else {                 // 4th quadrant
            return 2 * java.lang.Math.PI + asin(y / length)
        }
    }

    /**
     * Add some "noise" to this vector.
     *
     * @param random
     * the random number generator
     * @param amount
     * the amount of noise to add
     */
    fun wiggle(random: java.util.Random, amount: Double) {
        this.x += random.nextDouble() * amount - (amount / 2)
        this.y += random.nextDouble() * amount - (amount / 2)
    }

    /**
     * Returns the distance between two vectors.
     *
     * @param v2
     * second vector
     * @return distance between this and second vector
     */
    fun distance(v2: KVector): Double {
        val dx = this.x - v2.x
        val dy = this.y - v2.y
        return sqrt((dx * dx) + (dy * dy))
    }

    /**
     * Returns the dot product of the two given vectors.
     *
     * @param v2
     * second vector
     * @return this.x * v2.x + this.y * v2.y
     */
    fun dotProduct(v2: KVector): Double {
        return this.x * v2.x + this.y * v2.y
    }

    /**
     * Rotates the given vector v by the angle provided in radians. Rotation is anti-clockwise.
     * @param v
     * @param angle
     * @return the rotated vector
     */
    fun rotate(angle: Double): KVector {
        val newX = this.x * cos(angle) - this.y * sin(angle)
        this.y = this.x * sin(angle) + this.y * cos(angle)
        this.x = newX
        return this
    }

    /**
     * Returns the angle between this vector and another given vector in radians.
     * @param other
     * @return angle between vectors
     */
    fun angle(other: KVector): Double {
        return acos(this.dotProduct(other) / (this.length() * other.length()))
    }

    /**
     * Apply the given bounds to this vector.
     *
     * @param lowx
     * the lower bound for x coordinate
     * @param lowy
     * the lower bound for y coordinate
     * @param highx
     * the upper bound for x coordinate
     * @param highy
     * the upper bound for y coordinate
     * @return `this`
     * @throws IllegalArgumentException
     * if highx < lowx or highy < lowy
     */
    fun bound(
        lowx: Double, lowy: Double, highx: Double,
        highy: Double
    ): KVector {
        require(!(highx < lowx || highy < lowy)) { "The highx must be bigger then lowx and the highy must be bigger then lowy" }
        if (x < lowx) {
            x = lowx
        } else if (x > highx) {
            x = highx
        }
        if (y < lowy) {
            y = lowy
        } else if (y > highy) {
            y = highy
        }
        return this
    }

    val isNaN: Boolean
        /**
         * Determine whether any of the two values are NaN.
         *
         * @return true if x is NaN or y is NaN
         */
        get() = java.lang.Double.isNaN(x) || java.lang.Double.isNaN(y)

    val isInfinite: Boolean
        /**
         * Determine whether any of the two values are infinite.
         *
         * @return true if x is infinite or y is infinite
         */
        get() = java.lang.Double.isInfinite(x) || java.lang.Double.isInfinite(y)

    public override fun parse(string: String) {
        var start = 0
        while (start < string.length && isdelim(string.get(start), "([{\"' \t\r\n")) {
            start++
        }
        var end = string.length
        while (end > 0 && isdelim(string.get(end - 1), ")]}\"' \t\r\n")) {
            end--
        }
        require(start < end) { "The given string does not contain any numbers." }
        val tokens: Array<String?> = string.substring(start, end).split(",|;|\r|\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        require(tokens.size == 2) {
            ("Exactly two numbers are expected, "
                    + tokens.size + " were found.")
        }
        try {
            x = tokens[0]!!.trim { it <= ' ' }.toDouble()
            y = tokens[1]!!.trim { it <= ' ' }.toDouble()
        } catch (exception: java.lang.NumberFormatException) {
            throw java.lang.IllegalArgumentException(
                "The given string contains parts that cannot be parsed as numbers." + exception
            )
        }
    }

    companion object {
        /** the default fuzzyness used when comparing two vectors fuzzily.  */
        private const val DEFAULT_FUZZYNESS = 0.05

        /** the serial version UID.  */
        private val serialVersionUID = -4780985519832787684L

        /**
         * Returns the sum of arbitrarily many vectors as a new vector instance.
         *
         * @param vs vectors to be added
         * @return a new vector containing the sum of given vectors
         */
        fun sum(vararg vs: KVector): KVector {
            val sum = KVector()
            for (v in vs) {
                sum.x += v.x
                sum.y += v.y
            }
            return sum
        }

        /**
         * Returns the difference of two vectors as a new vector instance.
         *
         * @param v1
         * the minuend
         * @param v2
         * the subtrahend
         * @return a new vector containing the difference of given vectors
         */
        fun diff(v1: KVector, v2: KVector): KVector {
            return KVector(v1.x - v2.x, v1.y - v2.y)
        }

        /**
         * Calculates the cross product of two vectors v and w.
         * @param v
         * @param w
         * @return the cross product of v and w
         */
        fun crossProduct(v: KVector, w: KVector): Double {
            return v.x * w.y - v.y * w.x
        }

        /**
         * Determine whether the given character is a delimiter.
         *
         * @param c
         * a character
         * @param delims
         * a string of possible delimiters
         * @return true if `c` is one of the characters in `delims`
         */
        private fun isdelim(c: Char, delims: String): Boolean {
            for (i in 0..<delims.length) {
                if (c == delims.get(i)) {
                    return true
                }
            }
            return false
        }
    }
}
 */