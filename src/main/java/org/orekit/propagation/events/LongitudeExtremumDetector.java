/* Copyright 2002-2015 CS Systèmes d'Information
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
package org.orekit.propagation.events;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.FieldGeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.errors.OrekitException;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.handlers.EventHandler;
import org.orekit.propagation.events.handlers.StopOnIncreasing;

/** Detector for geographic longitude extremum.
 * <p>This detector identifies when a spacecraft reaches its
 * extremum longitudes with respect to a central body.</p>
 * @author Luc Maisonobe
 * @since 7.1
 */
public class LongitudeExtremumDetector extends AbstractDetector<LongitudeExtremumDetector> {

    /** Serializable UID. */
    private static final long serialVersionUID = 20150824L;

    /** Body on which the longitude is defined. */
    private OneAxisEllipsoid body;

    /** Build a new detector.
     * <p>The new instance uses default values for maximal checking interval
     * ({@link #DEFAULT_MAXCHECK}) and convergence threshold ({@link
     * #DEFAULT_THRESHOLD}).</p>
     * @param body body on which the longitude is defined
     */
    public LongitudeExtremumDetector(final OneAxisEllipsoid body) {
        this(DEFAULT_MAXCHECK, DEFAULT_THRESHOLD, body);
    }

    /** Build a detector.
     * @param maxCheck maximal checking interval (s)
     * @param threshold convergence threshold (s)
     * @param body body on which the longitude is defined
     */
    public LongitudeExtremumDetector(final double maxCheck, final double threshold,
                                    final OneAxisEllipsoid body) {
        this(maxCheck, threshold, DEFAULT_MAX_ITER, new StopOnIncreasing<LongitudeExtremumDetector>(),
             body);
    }

    /** Private constructor with full parameters.
     * <p>
     * This constructor is private as users are expected to use the builder
     * API with the various {@code withXxx()} methods to set up the instance
     * in a readable manner without using a huge amount of parameters.
     * </p>
     * @param maxCheck maximum checking interval (s)
     * @param threshold convergence threshold (s)
     * @param maxIter maximum number of iterations in the event time search
     * @param handler event handler to call at event occurrences
     * @param body body on which the longitude is defined
     */
    private LongitudeExtremumDetector(final double maxCheck, final double threshold,
                                     final int maxIter, final EventHandler<LongitudeExtremumDetector> handler,
                                     final OneAxisEllipsoid body) {
        super(maxCheck, threshold, maxIter, handler);
        this.body = body;
    }

    /** {@inheritDoc} */
    @Override
    protected LongitudeExtremumDetector create(final double newMaxCheck, final double newThreshold,
                                              final int newMaxIter,
                                              final EventHandler<LongitudeExtremumDetector> newHandler) {
        return new LongitudeExtremumDetector(newMaxCheck, newThreshold, newMaxIter, newHandler, body);
    }

    /** Get the body on which the geographic zone is defined.
     * @return body on which the geographic zone is defined
     */
    public BodyShape getBody() {
        return body;
    }

    /** Compute the value of the detection function.
     * <p>
     * The value is the spacecraft longitude time derivative.
     * </p>
     * @param s the current state information: date, kinematics, attitude
     * @return spacecraft longitude time derivative
     * @exception OrekitException if some specific error occurs
     */
    public double g(final SpacecraftState s) throws OrekitException {

        // convert state to geodetic coordinates
        final FieldGeodeticPoint<DerivativeStructure> gp =
                        body.transform(s.getPVCoordinates(), s.getFrame(), s.getDate());

        // longitude time derivative
        return gp.getLongitude().getPartialDerivative(1);

    }

}