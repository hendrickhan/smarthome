/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.smarthome.core.types.util;

import java.util.Set;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.UnitConverter;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.unit.ImperialUnits;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tec.uom.se.quantity.Quantities;
import tec.uom.se.unit.MetricPrefix;
import tec.uom.se.unit.TransformedUnit;

/**
 * A utility for parsing dimensions to interface classes of {@link Quantity} and parsing units from format strings.
 *
 * @author Henning Treu - initial contribution
 *
 */
@NonNullByDefault
public class UnitUtils {

    private static final Logger logger = LoggerFactory.getLogger(UnitUtils.class);

    public static final String UNIT_PLACEHOLDER = "%unit%";
    public static final String UNIT_PERCENT_FORMAT_STRING = "%%";

    private static final String JAVAX_MEASURE_QUANTITY_PREFIX = "javax.measure.quantity.";
    private static final String FRAMEWORK_DIMENSION_PREFIX = "org.eclipse.smarthome.core.library.dimension.";

    /**
     * Parses a String denoting a dimension (e.g. Length, Temperature, Mass,..) into a {@link Class} instance of an
     * interface from {@link javax.measure.Quantity}. The interfaces reside in {@code javax.measure.quantity} and
     * framework specific interfaces in {@code org.eclipse.smarthome.core.library.dimension}.
     *
     * @param dimension the simple name of an interface from the package {@code javax.measure.quantity} or
     *            {@code org.eclipse.smarthome.core.library.dimension}.
     * @return the {@link Class} instance of the interface or {@code null} if the given dimension is blank.
     * @throws IllegalArgumentException in case no class instance could be parsed from the given dimension.
     */
    public static @Nullable Class<? extends Quantity<?>> parseDimension(String dimension) {
        if (StringUtils.isBlank(dimension)) {
            return null;
        }

        try {
            return dimensionClass(FRAMEWORK_DIMENSION_PREFIX, dimension);
        } catch (ClassNotFoundException e1) {
            try {
                return dimensionClass(JAVAX_MEASURE_QUANTITY_PREFIX, dimension);
            } catch (ClassNotFoundException e2) {
                throw new IllegalArgumentException(
                        "Error creating a dimension Class instance for name '" + dimension + "'.");
            }
        }
    }

    /**
     * A utility method to parse a unit symbol from a given pattern (like stateDescription or widget label).
     * The unit is always expected to be the last part of the pattern separated by " " (e.g. "%.2f °C" for °C).
     *
     * @param pattern The pattern to extract the unit symbol from.
     * @return the unit symbol extracted from the pattern or {@code null} if the pattern did not match the expected
     *         format.
     */
    public static @Nullable Unit<?> parseUnit(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return null;
        }

        int lastBlankIndex = pattern.lastIndexOf(" ");
        if (lastBlankIndex < 0) {
            return null;
        }

        String unitSymbol = pattern.substring(lastBlankIndex).trim();
        if (StringUtils.isNotBlank(unitSymbol) && !unitSymbol.equals(UNIT_PLACEHOLDER)) {
            if (UNIT_PERCENT_FORMAT_STRING.equals(unitSymbol)) {
                return SmartHomeUnits.PERCENT;
            }
            try {
                Quantity<?> quantity = Quantities.getQuantity("1 " + unitSymbol);
                return quantity.getUnit();
            } catch (IllegalArgumentException e) {
                // we expect this exception in case the extracted string does not match any known unit
                logger.debug("Unknown unit from pattern: {}", unitSymbol);
            }
        }

        return null;
    }

    public static boolean isDifferentMeasurementSystem(Unit<? extends Quantity<?>> thisUnit, Unit<?> thatUnit) {
        Set<? extends Unit<?>> SI = SIUnits.getInstance().getUnits();
        Set<? extends Unit<?>> US = ImperialUnits.getInstance().getUnits();

        boolean differentSystems = (SI.contains(thisUnit) && US.contains(thatUnit)) //
                || (SI.contains(thatUnit) && US.contains(thisUnit));

        if (!differentSystems) {
            if (thisUnit instanceof TransformedUnit
                    && isMetricConversion(((TransformedUnit<?>) thisUnit).getConverter())) {
                return isDifferentMeasurementSystem(((TransformedUnit<?>) thisUnit).getParentUnit(), thatUnit);
            }

            if (thatUnit instanceof TransformedUnit
                    && isMetricConversion(((TransformedUnit<?>) thatUnit).getConverter())) {
                return isDifferentMeasurementSystem(thisUnit, ((TransformedUnit<?>) thatUnit).getParentUnit());
            }
        }

        return differentSystems;

    }

    private static boolean isMetricConversion(UnitConverter converter) {
        for (MetricPrefix mp : MetricPrefix.values()) {
            if (mp.getConverter().equals(converter)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Quantity<?>> dimensionClass(String prefix, String name)
            throws ClassNotFoundException {
        return (Class<? extends Quantity<?>>) Class.forName(prefix + name);
    }

}
