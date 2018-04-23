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
package org.orekit.propagation.semianalytical.dsst;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;

import org.hipparchus.Field;
import org.hipparchus.RealFieldElement;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.util.Decimal64Field;
import org.hipparchus.util.MathArrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FieldAttitude;
import org.orekit.attitudes.InertialProvider;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.errors.OrekitException;
import org.orekit.forces.drag.atmosphere.Atmosphere;
import org.orekit.forces.drag.atmosphere.HarrisPriester;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.FieldEquinoctialOrbit;
import org.orekit.orbits.FieldOrbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTAtmosphericDrag;
import org.orekit.propagation.semianalytical.dsst.forces.DSSTForceModel;
import org.orekit.propagation.semianalytical.dsst.utilities.FieldAuxiliaryElements;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedFieldAngularCoordinates;

public class FieldDSSTAtmosphericDragTest {
    
    @Test
    public void testGetMeanElementRate() throws IllegalArgumentException, OrekitException {
        doTestGetMeanElementRate(Decimal64Field.getInstance());
    }

    private <T extends RealFieldElement<T>> void doTestGetMeanElementRate(Field<T> field) throws IllegalArgumentException, OrekitException {
        
        final T zero = field.getZero();
        // Central Body geopotential 2x0
        final UnnormalizedSphericalHarmonicsProvider provider =
                GravityFieldFactory.getUnnormalizedProvider(2, 0);
        
        final Frame earthFrame = FramesFactory.getEME2000();
        final FieldAbsoluteDate<T> initDate = new FieldAbsoluteDate<>(field, 2003, 07, 01, 0, 0, 0, TimeScalesFactory.getUTC());
        
        // a  = 7204535.84810944 m
        // ex = -0.001119677138261611
        // ey = 5.333650671984143E-4
        // hx = 0.847841707880348
        // hy = 0.7998014061193262
        // lM = 3.897842092486239 rad
        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(zero.add(7204535.84810944),
                                                                zero.add(-0.001119677138261611),
                                                                zero.add(5.333650671984143E-4),
                                                                zero.add(0.847841707880348),
                                                                zero.add(0.7998014061193262),
                                                                zero.add(3.897842092486239),
                                                                PositionAngle.TRUE,
                                                                earthFrame,
                                                                initDate,
                                                                3.986004415E14);

        // Drag Force Model
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(provider.getAe(),
                                                            Constants.WGS84_EARTH_FLATTENING,
                                                            CelestialBodyFactory.getEarth().getBodyOrientedFrame());
        final Atmosphere atm = new HarrisPriester(CelestialBodyFactory.getSun(), earth, 6);
        final double cd = 2.0;
        final double area = 25.0;
        DSSTForceModel drag = new DSSTAtmosphericDrag(atm, cd, area);

        // Register the attitude provider to the force model
        Rotation rotation =  new Rotation(1., 0., 0., 0., false);
        AttitudeProvider attitudeProvider = new InertialProvider(rotation);
        drag.registerAttitudeProvider(attitudeProvider);
        
        // Attitude of the satellite
        FieldRotation<T> fieldRotation = new FieldRotation<>(field, rotation);
        FieldVector3D<T> rotationRate = new FieldVector3D<>(zero, zero, zero);
        FieldVector3D<T> rotationAcceleration = new FieldVector3D<>(zero, zero, zero);
        TimeStampedFieldAngularCoordinates<T> orientation = new TimeStampedFieldAngularCoordinates<>(initDate, fieldRotation, rotationRate, rotationAcceleration);
        final FieldAttitude<T> att = new FieldAttitude<>(earthFrame, orientation);
        
        final T mass = zero.add(1000.0);
        final FieldSpacecraftState<T> state = new FieldSpacecraftState<>(orbit, att, mass);
        final FieldAuxiliaryElements<T> auxiliaryElements = new FieldAuxiliaryElements<>(state.getOrbit(), 1);
        
        // Compute the mean element rate
        final T[] elements = MathArrays.buildArray(field, 7);
        Arrays.fill(elements, zero);
        final T[] daidt = drag.getMeanElementRate(state, auxiliaryElements);
        for (int i = 0; i < daidt.length; i++) {
            elements[i] = daidt[i];
        }
        Assert.assertEquals(-3.415320567871035E-5, elements[0].getReal(), 1.e-20);
        Assert.assertEquals(6.276312897745139E-13, elements[1].getReal(), 1.9e-27);
        Assert.assertEquals(-9.303357008691404E-13, elements[2].getReal(), 0.7e-27);
        Assert.assertEquals(-7.052316604063199E-14, elements[3].getReal(), 1.e-28);
        Assert.assertEquals(-6.793277250493389E-14, elements[4].getReal(), 3.e-29);
        Assert.assertEquals(-1.3565284454826392E-15, elements[5].getReal(), 1.e-27);
    
    }
    
    @Before
    public void setUp() throws OrekitException, IOException, ParseException {
        Utils.setDataRoot("regular-data:potential/shm-format");
    }
    
}
