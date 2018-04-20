/* Copyright 2002-2018 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.gnss.attitude;

import org.hipparchus.RealFieldElement;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.FieldAttitude;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.FieldPVCoordinatesProvider;
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.TimeStampedAngularCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 * Base class for attitude providers for navigation satellites.
 *
 * @author Luc Maisonobe
 * @since 9.2
 */
public abstract class AbstractGNSSAttitudeProvider implements GNSSAttitudeProvider {

    /** Serializable UID. */
    private static final long serialVersionUID = 20171114L;

    /** Start of validity for this provider. */
    private final AbsoluteDate validityStart;

    /** End of validity for this provider. */
    private final AbsoluteDate validityEnd;

    /** Provider for Sun position. */
    private final PVCoordinatesProvider sun;

    /** Simple constructor.
     * @param validityStart start of validity for this provider
     * @param validityEnd end of validity for this provider
     * @param sun provider for Sun position
     */
    protected AbstractGNSSAttitudeProvider(final AbsoluteDate validityStart,
                                           final AbsoluteDate validityEnd,
                                           final PVCoordinatesProvider sun) {
        this.validityStart = validityStart;
        this.validityEnd   = validityEnd;
        this.sun           = sun;
    }

    /** {@inheritDoc} */
    @Override
    public AbsoluteDate validityStart() {
        return validityStart;
    }

    /** {@inheritDoc} */
    @Override
    public AbsoluteDate validityEnd() {
        return validityEnd;
    }

    /** {@inheritDoc} */
    @Override
    public Attitude getAttitude(final PVCoordinatesProvider pvProv,
                                final AbsoluteDate date,
                                final Frame frame)
        throws OrekitException {

        // Sun/spacecraft geometry
        // computed in inertial frame so orbital plane (which depends on spacecraft velocity) is correct
        final TimeStampedPVCoordinates sunPV = sun.getPVCoordinates(date, frame);
        final TimeStampedPVCoordinates svPV  = pvProv.getPVCoordinates(date, frame);

        // compute yaw correction
        final TimeStampedAngularCoordinates corrected = correctedYaw(new GNSSAttitudeContext(sunPV, svPV));

        return new Attitude(frame, corrected);

    }

    /** {@inheritDoc} */
    @Override
    public <T extends RealFieldElement<T>> FieldAttitude<T> getAttitude(final FieldPVCoordinatesProvider<T> pvProv,
                                                                        final FieldAbsoluteDate<T> date,
                                                                        final Frame frame)
        throws OrekitException {
        // TODO
        return null;
    }

    /** Compute GNSS attitude with midnight/noon yaw turn correction.
     * @param context context data for attitude computation
     * @return corrected yaw
     * @exception OrekitException if yaw corrected attitude cannot be computed
     */
    protected abstract TimeStampedAngularCoordinates correctedYaw(GNSSAttitudeContext context)
        throws OrekitException;

}