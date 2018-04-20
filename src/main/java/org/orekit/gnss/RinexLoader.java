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
package org.orekit.gnss;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hipparchus.exception.DummyLocalizable;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.geometry.euclidean.twod.Vector2D;
import org.hipparchus.util.FastMath;
import org.orekit.data.DataLoader;
import org.orekit.data.DataProvidersManager;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

public class RinexLoader {

    /** Default supported files name pattern for rinex 2 observation files. */
    public static final String DEFAULT_RINEX_2_SUPPORTED_NAMES = "^\\w{4}\\d{3}[0a-x](?:\\d{2})?\\.\\d{2}[oO]$";

    /** Default supported files name pattern for rinex 3 observation files. */
    public static final String DEFAULT_RINEX_3_SUPPORTED_NAMES = "^\\w{9}_\\w{1}_\\d{11}_\\d{2}\\w_\\d{2}\\w{1}_\\w{2}\\.rnx$";

    // CHECKSTYLE: stop JavadocVariable check
    private static final String RINEX_VERSION_TYPE   = "RINEX VERSION / TYPE";
    private static final String COMMENT              = "COMMENT";
    private static final String PGM_RUN_BY_DATE      = "PGM / RUN BY / DATE";
    private static final String MARKER_NAME          = "MARKER NAME";
    private static final String MARKER_NUMBER        = "MARKER NUMBER";
    private static final String MARKER_TYPE          = "MARKER TYPE";
    private static final String OBSERVER_AGENCY      = "OBSERVER / AGENCY";
    private static final String REC_NB_TYPE_VERS     = "REC # / TYPE / VERS";
    private static final String ANT_NB_TYPE          = "ANT # / TYPE";
    private static final String APPROX_POSITION_XYZ  = "APPROX POSITION XYZ";
    private static final String ANTENNA_DELTA_H_E_N  = "ANTENNA: DELTA H/E/N";
    private static final String ANTENNA_DELTA_X_Y_Z  = "ANTENNA: DELTA X/Y/Z";
    private static final String ANTENNA_PHASECENTER  = "ANTENNA: PHASECENTER";
    private static final String ANTENNA_B_SIGHT_XYZ  = "ANTENNA: B.SIGHT XYZ";
    private static final String ANTENNA_ZERODIR_AZI  = "ANTENNA: ZERODIR AZI";
    private static final String ANTENNA_ZERODIR_XYZ  = "ANTENNA: ZERODIR XYZ";
    private static final String NB_OF_SATELLITES     = "# OF SATELLITES";
    private static final String WAVELENGTH_FACT_L1_2 = "WAVELENGTH FACT L1/2";
    private static final String RCV_CLOCK_OFFS_APPL  = "RCV CLOCK OFFS APPL";
    private static final String INTERVAL             = "INTERVAL";
    private static final String TIME_OF_FIRST_OBS    = "TIME OF FIRST OBS";
    private static final String TIME_OF_LAST_OBS     = "TIME OF LAST OBS";
    private static final String LEAP_SECONDS         = "LEAP SECONDS";
    private static final String PRN_NB_OF_OBS        = "PRN / # OF OBS";
    private static final String NB_TYPES_OF_OBSERV   = "# / TYPES OF OBSERV";
    private static final String END_OF_HEADER        = "END OF HEADER";
    private static final String CENTER_OF_MASS_XYZ   = "CENTER OF MASS: XYZ";
    private static final String SIGNAL_STRENGTH_UNIT = "SIGNAL STRENGTH UNIT";
    private static final String SYS_NB_OBS_TYPES     = "SYS / # / OBS TYPES";
    private static final String SYS_DCBS_APPLIED     = "SYS / DCBs APPLIED";
    private static final String SYS_PCVS_APPLIED     = "SYS / PCVS APPLIED";
    private static final String SYS_SCALE_FACTOR     = "SYS / SCALE FACTOR";
    private static final String SYS_PHASE_SHIFT      = "SYS / PHASE SHIFT";
    private static final String GLONASS_SLOT_FRQ_NB  = "GLONASS SLOT / FRQ #";
    private static final String GLONASS_COD_PHS_BIS  = "GLONASS COD/PHS/BIS";

    private static final String GPS                  = "GPS";
    private static final String GAL                  = "GAL";
    private static final String GLO                  = "GLO";
    private static final String QZS                  = "QZS";
    private static final String BDT                  = "BDT";
    private static final String IRN                  = "IRN";
    // CHECKSTYLE: resume JavadocVariable check

    /** Rinex Observations, grouped by file. */
    private final Map<RinexHeader, List<ObservationDataSet>> observations;

    /** Simple constructor.
     * <p>
     * This constructor is used when the rinex files are managed by the
     * global {@link DataProvidersManager DataProvidersManager}.
     * </p>
     * @param supportedNames regular expression for supported files names
     * @exception OrekitException if no rinex file can be read
     */
    public RinexLoader(final String supportedNames)
        throws OrekitException {
        observations = new HashMap<>();
        DataProvidersManager.getInstance().feed(supportedNames, new Parser());
    }

    /** Simple constructor.
     * @param input data input stream
     * @param name name of the file (or zip entry)
     * @exception OrekitException if no rinex file can be read
     */
    public RinexLoader(final InputStream input, final String name)
        throws OrekitException {
        try {
            observations = new HashMap<>();
            new Parser().loadData(input, name);
        } catch (IOException ioe) {
            throw new OrekitException(ioe, new DummyLocalizable(ioe.getMessage()));
        }
    }

    /** Add a Rinex header.
     * @param header rinex header to add
     * @return the list into which observations should be added
     */
    private List<ObservationDataSet> addHeader(final RinexHeader header) {
        final List<ObservationDataSet> list = new ArrayList<>();
        observations.put(header, list);
        return list;
    }

    /** Get parsed rinex observations.
     * @return unmodifiable view of parsed rinex observations
     */
    public Map<RinexHeader, List<ObservationDataSet>> getObservations() {
        return Collections.unmodifiableMap(observations);
    }

    /** Parser for rinex files.
     */
    public class Parser implements DataLoader {

        /** Index of label in data lines. */
        private static final int LABEL_START = 60;

        /** File type Accepted (only Observation Data). */
        private static final String FILE_TYPE = "O"; //Only Observation Data files

        /** {@inheritDoc} */
        @Override
        public boolean stillAcceptsData() {
            // we load all rinex files we can find
            return true;
        }

        /** {@inheritDoc} */
        @Override
        public void loadData(final InputStream input, final String name)
            throws IOException, OrekitException {

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {

                // placeholders for parsed data
                SatelliteSystem                  satelliteSystem        = null;
                double                           formatVersion          = Double.NaN;
                boolean                          inRinexVersion         = false;
                int                              lineNumber             = 0;
                SatelliteSystem                  obsTypesSystem         = null;
                String                           markerName             = null;
                String                           markerNumber           = null;
                String                           markerType             = null;
                String                           observerName           = null;
                String                           agencyName             = null;
                String                           receiverNumber         = null;
                String                           receiverType           = null;
                String                           receiverVersion        = null;
                String                           antennaNumber          = null;
                String                           antennaType            = null;
                Vector3D                         approxPos              = null;
                Vector3D                         antRefPoint            = null;
                String                           obsCode                = null;
                Vector3D                         antPhaseCenter         = null;
                Vector3D                         antBSight              = null;
                double                           antAzi                 = Double.NaN;
                Vector3D                         antZeroDir             = null;
                Vector3D                         centerMass             = null;
                double                           antHeight              = Double.NaN;
                Vector2D                         eccentricities         = Vector2D.ZERO;
                int                              clkOffset              = -1;
                int                              nbTypes                = -1;
                int                              nbSat                  = -1;
                double                           interval               = Double.NaN;
                AbsoluteDate                     tFirstObs              = AbsoluteDate.PAST_INFINITY;
                AbsoluteDate                     tLastObs               = AbsoluteDate.FUTURE_INFINITY;
                TimeScale                        timeScale              = null;
                String                           timeScaleStr           = null;
                int                              leapSeconds            = 0;
                AbsoluteDate                     tObs                   = AbsoluteDate.PAST_INFINITY;
                String[]                         satsObsList            = null;
                String                           strYear                = null;
                int                              eventFlag              = -1;
                int                              nbSatObs               = -1;
                int                              nbLinesSat             = -1;
                double                           rcvrClkOffset          = 0;
                boolean                          inRunBy                = false;
                boolean                          inMarkerName           = false;
                boolean                          inMarkerType           = false;
                boolean                          inObserver             = false;
                boolean                          inRecType              = false;
                boolean                          inAntType              = false;
                boolean                          inAproxPos             = false;
                boolean                          inAntDelta             = false;
                boolean                          inTypesObs             = false;
                boolean                          inFirstObs             = false;
                boolean                          inPhaseShift           = false;
                boolean                          inGlonassSlot          = false;
                boolean                          inGlonassCOD           = false;
                List<ObservationDataSet>         observationsList       = null;

                //First line must  always contain Rinex Version, File Type and Satellite Systems Observed
                String line = reader.readLine();
                lineNumber++;
                formatVersion = parseDouble(line, 0, 9);
                int format100 = (int) FastMath.rint(100 * formatVersion);

                if ((format100 != 200) && (format100 != 210) && (format100 != 211) &&
                    (format100 != 300) && (format100 != 301) && (format100 != 302) && (format100 != 303)) {
                    throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, name);
                }

                //File Type must be Observation_Data
                if (!(parseString(line, 20, 1)).equals(FILE_TYPE)) {
                    throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, name);
                }
                satelliteSystem = SatelliteSystem.parseSatelliteSystem(parseString(line, 40, 1));
                inRinexVersion = true;

                switch (format100 / 100) {
                    case 2:

                        final int                   MAX_OBS_TYPES_PER_LINE_RNX2 = 9;
                        final int                   MAX_N_SAT_OBSERVATION       = 12;
                        final int                   MAX_N_TYPES_OBSERVATION     = 5;
                        final List<RinexFrequency>  typesObs = new ArrayList<>();

                        for (line = reader.readLine(); line != null; line = reader.readLine()) {
                            ++lineNumber;

                            if (observationsList == null) {
                                switch(line.substring(LABEL_START).trim()) {
                                    case RINEX_VERSION_TYPE :

                                        formatVersion = parseDouble(line, 0, 9);
                                        //File Type must be Observation_Data
                                        if (!(parseString(line, 20, 1)).equals(FILE_TYPE)) {
                                            throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, name);
                                        }
                                        satelliteSystem = SatelliteSystem.parseSatelliteSystem(parseString(line, 40, 1));
                                        inRinexVersion = true;
                                        break;
                                    case COMMENT :
                                        // nothing to do
                                        break;
                                    case PGM_RUN_BY_DATE :
                                        inRunBy = true;
                                        break;
                                    case MARKER_NAME :
                                        markerName = parseString(line, 0, 60);
                                        inMarkerName = true;
                                        break;
                                    case MARKER_NUMBER :
                                        markerNumber = parseString(line, 0, 20);
                                        break;
                                    case OBSERVER_AGENCY :
                                        observerName = parseString(line, 0, 20);
                                        agencyName   = parseString(line, 20, 40);
                                        inObserver = true;
                                        break;
                                    case REC_NB_TYPE_VERS :
                                        receiverNumber  = parseString(line, 0, 20);
                                        receiverType    = parseString(line, 20, 20);
                                        receiverVersion = parseString(line, 40, 20);
                                        inRecType = true;
                                        break;
                                    case ANT_NB_TYPE :
                                        antennaNumber = parseString(line, 0, 20);
                                        antennaType   = parseString(line, 20, 20);
                                        inAntType = true;
                                        break;
                                    case APPROX_POSITION_XYZ :
                                        approxPos = new Vector3D(parseDouble(line, 0, 14), parseDouble(line, 14, 14),
                                                                 parseDouble(line, 28, 14));
                                        inAproxPos = true;
                                        break;
                                    case ANTENNA_DELTA_H_E_N :
                                        antHeight = parseDouble(line, 0, 14);
                                        eccentricities = new Vector2D(parseDouble(line, 14, 14), parseDouble(line, 28, 14));
                                        inAntDelta = true;
                                        break;
                                    case NB_OF_SATELLITES :
                                        nbSat = parseInt(line, 0, 6);
                                        break;
                                    case WAVELENGTH_FACT_L1_2 :
                                        //Optional line in header
                                        //Not stored for now
                                        break;
                                    case RCV_CLOCK_OFFS_APPL :
                                        clkOffset = parseInt(line, 0, 6);
                                        break;
                                    case INTERVAL :
                                        interval = parseDouble(line, 0, 10);
                                        break;
                                    case TIME_OF_FIRST_OBS :
                                        switch (satelliteSystem) {
                                            case GPS:
                                                timeScale = TimeScalesFactory.getGPS();
                                                break;
                                            case GALILEO:
                                                timeScale = TimeScalesFactory.getGST();
                                                break;
                                            case GLONASS:
                                                timeScale = TimeScalesFactory.getGLONASS();
                                                break;
                                            case MIXED:
                                                //in Case of Mixed data, Timescale must be specified in the Time of First line
                                                timeScaleStr = parseString(line, 48, 3);

                                                if (timeScaleStr.equals(GPS)) {
                                                    timeScale = TimeScalesFactory.getGPS();
                                                } else if (timeScaleStr.equals(GAL)) {
                                                    timeScale = TimeScalesFactory.getGST();
                                                } else if (timeScaleStr.equals(GLO)) {
                                                    timeScale = TimeScalesFactory.getGLONASS();
                                                } else {
                                                    throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, name);
                                                }
                                                break;
                                            default :
                                                throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                                          lineNumber, name, line);
                                        }

                                        tFirstObs = new AbsoluteDate(parseInt(line, 0, 6),
                                                                     parseInt(line, 6, 6),
                                                                     parseInt(line, 12, 6),
                                                                     parseInt(line, 18, 6),
                                                                     parseInt(line, 24, 6),
                                                                     parseDouble(line, 30, 13), timeScale);
                                        inFirstObs = true;
                                        break;
                                    case TIME_OF_LAST_OBS :
                                        tLastObs = new AbsoluteDate(parseInt(line, 0, 6),
                                                                    parseInt(line, 6, 6),
                                                                    parseInt(line, 12, 6),
                                                                    parseInt(line, 18, 6),
                                                                    parseInt(line, 24, 6),
                                                                    parseDouble(line, 30, 13), timeScale);
                                        break;
                                    case LEAP_SECONDS :
                                        leapSeconds = parseInt(line, 0, 6);
                                        break;
                                    case PRN_NB_OF_OBS :
                                        //Optional line in header, indicates number of Observations par Satellite
                                        //Not stored for now
                                        break;
                                    case NB_TYPES_OF_OBSERV :
                                        nbTypes = parseInt(line, 0, 6);
                                        final int nbLinesTypesObs = (nbTypes + MAX_OBS_TYPES_PER_LINE_RNX2 - 1 ) / MAX_OBS_TYPES_PER_LINE_RNX2;

                                        for (int j = 0; j < nbLinesTypesObs; j++) {
                                            if (j > 0) {
                                                line = reader.readLine(); //Next line
                                                lineNumber++;
                                            }
                                            final int iMax = FastMath.min(MAX_OBS_TYPES_PER_LINE_RNX2, nbTypes - typesObs.size());
                                            for (int i = 0; i < iMax; i++) {
                                                try {
                                                    typesObs.add(RinexFrequency.valueOf(parseString(line, 10 + (6 * i), 2)));
                                                } catch (IllegalArgumentException iae) {
                                                    throw new OrekitException(OrekitMessages.UNKNOWN_RINEX_FREQUENCY,
                                                                              parseString(line, 10 + (6 * i), 2), name, lineNumber);
                                                }
                                            }
                                        }
                                        inTypesObs = true;
                                        break;
                                    case END_OF_HEADER :
                                        //We make sure that we have read all the mandatory fields inside the header of the Rinex
                                        if (!inRinexVersion || !inRunBy || !inMarkerName ||
                                            !inObserver || !inRecType || !inAntType ||
                                            !inAproxPos || !inAntDelta || !inTypesObs || !inFirstObs) {
                                            throw new OrekitException(OrekitMessages.INCOMPLETE_HEADER, name);
                                        }

                                        //Header information gathered
                                        observationsList = addHeader(new RinexHeader(formatVersion, satelliteSystem,
                                                                                     markerName, markerNumber, observerName,
                                                                                     agencyName, receiverNumber, receiverType,
                                                                                     receiverVersion, antennaNumber, antennaType,
                                                                                     approxPos, antHeight, eccentricities, interval,
                                                                                     tFirstObs, tLastObs, clkOffset, leapSeconds));
                                        break;
                                    default :
                                        if (observationsList == null) {
                                            //There must be an error due to an unknown Label inside the Header
                                            throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                                      lineNumber, name, line);
                                        }
                                }
                            } else {

                                //Start of a new Observation
                                rcvrClkOffset     =  0;
                                nbLinesSat        = -1;
                                eventFlag         = -1;
                                nbSatObs          = -1;
                                satsObsList       = null;
                                tObs              = null;
                                strYear           = null;

                                eventFlag = parseInt(line, 28, 1);
                                //If eventFlag>1, we skip the corresponding lines to the next observation
                                if (eventFlag != 0) {
                                    if (eventFlag == 6) {
                                        nbSatObs  = parseInt(line, 29, 3);
                                        nbLinesSat = (nbSatObs + 12 - 1) / 12;
                                        final int nbLinesObs = (nbTypes + 5 - 1) / 5;
                                        final int nbLinesSkip = (nbLinesSat - 1) + nbSatObs * nbLinesObs;
                                        for (int i = 0; i < nbLinesSkip; i++) {
                                            line = reader.readLine(); //Next line
                                            lineNumber++;
                                        }
                                    } else {
                                        final int nbLinesSkip = parseInt(line, 29, 3);
                                        for (int i = 0; i < nbLinesSkip; i++) {
                                            line = reader.readLine(); //Next line
                                            lineNumber++;
                                        }
                                    }
                                } else {

                                    final int y = Integer.parseInt(parseString(line, 0, 3));
                                    if (79 < y && y <= 99) {
                                        strYear = "19" + y;
                                    } else if (0 <= y && y <= 79) {
                                        strYear = "20" + parseString(line, 0, 3);
                                    }
                                    tObs = new AbsoluteDate(Integer.parseInt(strYear),
                                                            parseInt(line, 3, 3),
                                                            parseInt(line, 6, 3),
                                                            parseInt(line, 9, 3),
                                                            parseInt(line, 12, 3),
                                                            parseDouble(line, 15, 11), timeScale);

                                    nbSatObs  = parseInt(line, 29, 3);
                                    satsObsList   = new String[nbSatObs];
                                    //If the total number of satellites was indicated in the Header
                                    if (nbSat != -1 && nbSatObs > nbSat) {
                                        //we check that the number of Sat in the observation is consistent
                                        throw new OrekitException(OrekitMessages.INCONSISTENT_NUMBER_OF_SATS,
                                                                  lineNumber, name, nbSatObs, nbSat);
                                    }

                                    nbLinesSat = (nbSatObs + MAX_N_SAT_OBSERVATION - 1) / MAX_N_SAT_OBSERVATION;
                                    for (int j = 0; j < nbLinesSat; j++) {
                                        if (j > 0) {
                                            line = reader.readLine(); //Next line
                                            lineNumber++;
                                        }
                                        final int iMax = FastMath.min(MAX_N_SAT_OBSERVATION, nbSatObs  - j * MAX_N_SAT_OBSERVATION);
                                        for (int i = 0; i < iMax; i++) {
                                            satsObsList[i + MAX_N_SAT_OBSERVATION * j] = parseString(line, 32 + 3 * i, 3);
                                        }

                                        //Read the Receiver Clock offset, if present
                                        rcvrClkOffset = parseDouble(line, 68, 12);

                                    }

                                    //For each one of the Satellites in this observation
                                    final int nbLinesObs = (nbTypes + MAX_N_TYPES_OBSERVATION - 1) / MAX_N_TYPES_OBSERVATION;
                                    for (int k = 0; k < nbSatObs; k++) {


                                        //Once the Date and Satellites list is read:
                                        //  - to read the Data for each satellite
                                        //  - 5 Observations per line
                                        final List<ObservationData> observationData = new ArrayList<>(nbSatObs);
                                        for (int j = 0; j < nbLinesObs; j++) {
                                            line = reader.readLine(); //Next line
                                            lineNumber++;
                                            final int iMax = FastMath.min(MAX_N_TYPES_OBSERVATION, nbTypes - observationData.size());
                                            for (int i = 0; i < iMax; i++) {
                                                observationData.add(new ObservationData(typesObs.get(observationData.size()),
                                                                                        parseDouble(line, 16 * i, 14),
                                                                                        parseInt(line, 14 + 16 * i, 1),
                                                                                        parseInt(line, 15 + 16 * i, 1)));
                                            }
                                        }

                                        //We check that the Satellite type is consistent with Satellite System in the top of the file
                                        final SatelliteSystem satelliteSystemSat = SatelliteSystem.parseSatelliteSystem(satsObsList[k]);
                                        if (!satelliteSystem.equals(SatelliteSystem.MIXED)) {
                                            if (!satelliteSystemSat.equals(satelliteSystem)) {
                                                throw new OrekitException(OrekitMessages.INCONSISTENT_SATELLITE_SYSTEM,
                                                                          lineNumber, name, satelliteSystem, satelliteSystemSat);
                                            }
                                        }

                                        final int prnNumber;
                                        switch (satelliteSystemSat) {
                                            case GPS:
                                            case GLONASS:
                                            case GALILEO:
                                                prnNumber = Integer.parseInt(satsObsList[k].substring(1, 3).trim());
                                                break;
                                            case SBAS:
                                                prnNumber = Integer.parseInt(satsObsList[k].substring(1, 3).trim()) + 100;
                                                break;
                                            default:
                                                // MIXED satellite system is not allowed here
                                                throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                                          lineNumber, name, line);
                                        }

                                        observationsList.add(new ObservationDataSet(satelliteSystemSat, prnNumber, tObs, rcvrClkOffset,
                                                                                    observationData));

                                    }
                                }
                            }
                        }
                        break;
                    case 3:

                        final int                   MAX_OBS_TYPES_PER_LINE_RNX3 = 13;
                        final int           MAX_OBS_TYPES_SCALE_FACTOR_PER_LINE = 12;
                        final int                    MAX_N_SAT_PHSHIFT_PER_LINE = 10;

                        final Map<SatelliteSystem, List<RinexFrequency>> listTypeObs            = new HashMap<>();
                        final List<RinexFrequency>                       typeObs                = new ArrayList<>();
                        String                                           sigStrengthUnit        = null;
                        int                                              leapSecondsFuture      = 0;
                        int                                              leapSecondsWeekNum     = 0;
                        int                                              leapSecondsDayNum      = 0;
                        final List<AppliedDCBs>                          listAppliedDCBs        = new ArrayList<>();
                        final List<AppliedPCVS>                          listAppliedPCVS        = new ArrayList<>();
                        SatelliteSystem                                  satSystemScaleFactor   = null;
                        int                                              scaleFactor            = 1;
                        int                                              nbObsScaleFactor       = 0;
                        final List<RinexFrequency>                       typesObsScaleFactor    = new ArrayList<>();
                        final List<ScaleFactorCorrection>                scaleFactorCorrections = new ArrayList<>();
                        String[]                                         satsPhaseShift         = null;
                        int                                              nbSatPhaseShift        = 0;
                        SatelliteSystem                                  satSystemPhaseShift    = null;
                        double                                           corrPhaseShift         = 0.0;
                        final List<PhaseShiftCorrection>                 phaseShiftCorrections  = new ArrayList<>();
                        RinexFrequency                                   phaseShiftTypeObs      = null;


                        for (line = reader.readLine(); line != null; line = reader.readLine()) {
                            ++lineNumber;
                            if (observationsList == null) {
                                switch(line.substring(LABEL_START).trim()) {
                                    case RINEX_VERSION_TYPE : {
                                        formatVersion = parseDouble(line, 0, 9);
                                        format100     = (int) FastMath.rint(100 * formatVersion);
                                        if ((format100 != 300) && (format100 != 301) && (format100 != 302) && (format100 != 303)) {
                                            throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, name);
                                        }
                                        //File Type must be Observation_Data
                                        if (!(parseString(line, 20, 1)).equals(FILE_TYPE)) {
                                            throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, name);
                                        }
                                        satelliteSystem = SatelliteSystem.parseSatelliteSystem(parseString(line, 40, 1));
                                        inRinexVersion = true;
                                    }
                                        break;
                                    case COMMENT :
                                        // nothing to do
                                        break;
                                    case PGM_RUN_BY_DATE :
                                        inRunBy = true;
                                        break;
                                    case MARKER_NAME :
                                        markerName = parseString(line, 0, 60);
                                        inMarkerName = true;
                                        break;
                                    case MARKER_NUMBER :
                                        markerNumber = parseString(line, 0, 20);
                                        break;
                                    case MARKER_TYPE :
                                        markerType = parseString(line, 0, 20);
                                        inMarkerType = true;
                                        //Could be done with an Enumeration
                                        break;
                                    case OBSERVER_AGENCY :
                                        observerName = parseString(line, 0, 20);
                                        agencyName   = parseString(line, 20, 40);
                                        inObserver = true;
                                        break;
                                    case REC_NB_TYPE_VERS :
                                        receiverNumber  = parseString(line, 0, 20);
                                        receiverType    = parseString(line, 20, 20);
                                        receiverVersion = parseString(line, 40, 20);
                                        inRecType = true;
                                        break;
                                    case ANT_NB_TYPE :
                                        antennaNumber = parseString(line, 0, 20);
                                        antennaType   = parseString(line, 20, 20);
                                        inAntType = true;
                                        break;
                                    case APPROX_POSITION_XYZ :
                                        approxPos = new Vector3D(parseDouble(line, 0, 14),
                                                                 parseDouble(line, 14, 14),
                                                                 parseDouble(line, 28, 14));
                                        inAproxPos = true;
                                        break;
                                    case ANTENNA_DELTA_H_E_N :
                                        antHeight = parseDouble(line, 0, 14);
                                        eccentricities = new Vector2D(parseDouble(line, 14, 14),
                                                                      parseDouble(line, 28, 14));
                                        inAntDelta = true;
                                        break;
                                    case ANTENNA_DELTA_X_Y_Z :
                                        antRefPoint = new Vector3D(parseDouble(line, 0, 14),
                                                                   parseDouble(line, 14, 14),
                                                                   parseDouble(line, 28, 14));
                                        break;
                                    case ANTENNA_PHASECENTER :
                                        obsCode = parseString(line, 2, 3);
                                        antPhaseCenter = new Vector3D(parseDouble(line, 5, 9),
                                                                      parseDouble(line, 14, 14),
                                                                      parseDouble(line, 28, 14));
                                        break;
                                    case ANTENNA_B_SIGHT_XYZ :
                                        antBSight = new Vector3D(parseDouble(line, 0, 14),
                                                                 parseDouble(line, 14, 14),
                                                                 parseDouble(line, 28, 14));
                                        break;
                                    case ANTENNA_ZERODIR_AZI :
                                        antAzi = parseDouble(line, 0, 14);
                                        break;
                                    case ANTENNA_ZERODIR_XYZ :
                                        antZeroDir = new Vector3D(parseDouble(line, 0, 14),
                                                                  parseDouble(line, 14, 14),
                                                                  parseDouble(line, 28, 14));
                                        break;
                                    case CENTER_OF_MASS_XYZ :
                                        centerMass = new Vector3D(parseDouble(line, 0, 14),
                                                                  parseDouble(line, 14, 14),
                                                                  parseDouble(line, 28, 14));
                                        break;
                                    case NB_OF_SATELLITES :
                                        nbSat = parseInt(line, 0, 6);
                                        break;
                                    case RCV_CLOCK_OFFS_APPL :
                                        clkOffset = parseInt(line, 0, 6);
                                        break;
                                    case INTERVAL :
                                        interval = parseDouble(line, 0, 10);
                                        break;
                                    case TIME_OF_FIRST_OBS :
                                        switch(satelliteSystem) {
                                            case GPS:
                                                timeScale = TimeScalesFactory.getGPS();
                                                break;
                                            case GALILEO:
                                                timeScale = TimeScalesFactory.getGST();
                                                break;
                                            case GLONASS:
                                                timeScale = TimeScalesFactory.getGLONASS();
                                                break;
                                            case QZSS:
                                                timeScale = TimeScalesFactory.getQZSS();
                                                break;
                                            case COMPASS:
                                                timeScale = TimeScalesFactory.getBDT();
                                                break;
                                            case IRNSS:
                                                timeScale = TimeScalesFactory.getIRNSST();
                                                break;
                                            case MIXED:
                                                //in Case of Mixed data, Timescale must be specified in the Time of First line
                                                timeScaleStr = parseString(line, 48, 3);

                                                if (timeScaleStr.equals(GPS)) {
                                                    timeScale = TimeScalesFactory.getGPS();
                                                } else if (timeScaleStr.equals(GAL)) {
                                                    timeScale = TimeScalesFactory.getGST();
                                                } else if (timeScaleStr.equals(GLO)) {
                                                    timeScale = TimeScalesFactory.getGLONASS();
                                                } else if (timeScaleStr.equals(QZS)) {
                                                    timeScale = TimeScalesFactory.getQZSS();
                                                } else if (timeScaleStr.equals(BDT)) {
                                                    timeScale = TimeScalesFactory.getBDT();
                                                } else if (timeScaleStr.equals(IRN)) {
                                                    timeScale = TimeScalesFactory.getIRNSST();
                                                } else {
                                                    throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, name);
                                                }
                                                break;
                                            default :
                                                throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                                          lineNumber, name, line);
                                        }

                                        tFirstObs = new AbsoluteDate(parseInt(line, 0, 6),
                                                                     parseInt(line, 6, 6),
                                                                     parseInt(line, 12, 6),
                                                                     parseInt(line, 18, 6),
                                                                     parseInt(line, 24, 6),
                                                                     parseDouble(line, 30, 13), timeScale);
                                        inFirstObs = true;
                                        break;
                                    case TIME_OF_LAST_OBS :
                                        tLastObs = new AbsoluteDate(parseInt(line, 0, 6),
                                                                    parseInt(line, 6, 6),
                                                                    parseInt(line, 12, 6),
                                                                    parseInt(line, 18, 6),
                                                                    parseInt(line, 24, 6),
                                                                    parseDouble(line, 30, 13), timeScale);
                                        break;
                                    case LEAP_SECONDS :
                                        leapSeconds = parseInt(line, 0, 6);
                                        leapSecondsFuture = parseInt(line, 6, 6);
                                        leapSecondsWeekNum = parseInt(line, 12, 6);
                                        leapSecondsDayNum = parseInt(line, 18, 6);
                                        //Time System Identifier must be added, last A3 String
                                        break;
                                    case PRN_NB_OF_OBS :
                                        //Optional line in header, indicates number of Observations par Satellite
                                        //Not stored for now
                                        break;
                                    case SYS_NB_OBS_TYPES :
                                        obsTypesSystem = null;
                                        typeObs.clear();

                                        obsTypesSystem = SatelliteSystem.parseSatelliteSystem(parseString(line, 0, 1));
                                        nbTypes = parseInt(line, 3, 3);

                                        final int nbLinesTypesObs = (nbTypes + MAX_OBS_TYPES_PER_LINE_RNX3 - 1) / MAX_OBS_TYPES_PER_LINE_RNX3;
                                        for (int j = 0; j < nbLinesTypesObs; j++) {
                                            if (j > 0) {
                                                line = reader.readLine(); //Next line
                                                lineNumber++;
                                            }
                                            final int iMax = FastMath.min(MAX_OBS_TYPES_PER_LINE_RNX3, nbTypes - typeObs.size());
                                            for (int i = 0; i < iMax; i++) {
                                                try {
                                                    typeObs.add(RinexFrequency.valueOf(parseString(line, 7 + (4 * i), 3)));
                                                } catch (IllegalArgumentException iae) {
                                                    throw new OrekitException(OrekitMessages.UNKNOWN_RINEX_FREQUENCY,
                                                                              parseString(line, 7 + (4 * i), 3), name, lineNumber);
                                                }
                                            }
                                        }
                                        listTypeObs.put(obsTypesSystem, new ArrayList<>(typeObs));
                                        inTypesObs = true;
                                        break;
                                    case SIGNAL_STRENGTH_UNIT :
                                        sigStrengthUnit = parseString(line, 0, 20);
                                        break;
                                    case SYS_DCBS_APPLIED :

                                        listAppliedDCBs.add(new AppliedDCBs(parseString(line, 2, 17), parseString(line, 20, 40)));
                                        break;
                                    case SYS_PCVS_APPLIED :

                                        listAppliedPCVS.add(new AppliedPCVS(parseString(line, 2, 17), parseString(line, 20, 40)));
                                        break;
                                    case SYS_SCALE_FACTOR :
                                        satSystemScaleFactor  = null;
                                        scaleFactor           = 1;
                                        nbObsScaleFactor      = 0;

                                        satSystemScaleFactor = SatelliteSystem.parseSatelliteSystem(parseString(line, 0, 1));
                                        scaleFactor          = parseInt(line, 2, 4);
                                        nbObsScaleFactor     = parseInt(line, 8, 2);

                                        if (nbObsScaleFactor == 0) {
                                            typesObsScaleFactor.addAll(listTypeObs.get(satSystemScaleFactor));
                                        } else {
                                            final int nbLinesTypesObsScaleFactor = (nbObsScaleFactor + MAX_OBS_TYPES_SCALE_FACTOR_PER_LINE - 1) /
                                                                                   MAX_OBS_TYPES_SCALE_FACTOR_PER_LINE;
                                            for (int j = 0; j < nbLinesTypesObsScaleFactor; j++) {
                                                if ( j > 0) {
                                                    line = reader.readLine(); //Next line
                                                    lineNumber++;
                                                }
                                                final int iMax = FastMath.min(MAX_OBS_TYPES_SCALE_FACTOR_PER_LINE, nbObsScaleFactor - typesObsScaleFactor.size());
                                                for (int i = 0; i < iMax; i++) {
                                                    typesObsScaleFactor.add(RinexFrequency.valueOf(parseString(line, 11 + (4 * i), 3)));
                                                }
                                            }
                                        }

                                        scaleFactorCorrections.add(new ScaleFactorCorrection(satSystemScaleFactor,
                                                                                             scaleFactor, typesObsScaleFactor));
                                        break;
                                    case SYS_PHASE_SHIFT :

                                        nbSatPhaseShift     = 0;
                                        satsPhaseShift      = null;
                                        corrPhaseShift      = 0.0;
                                        phaseShiftTypeObs   = null;
                                        satSystemPhaseShift = null;

                                        satSystemPhaseShift = SatelliteSystem.parseSatelliteSystem(parseString(line, 0, 1));
                                        phaseShiftTypeObs = RinexFrequency.valueOf(parseString(line, 2, 3));
                                        nbSatPhaseShift = parseInt(line, 16, 2);
                                        corrPhaseShift = parseDouble(line, 6, 8);

                                        if (nbSatPhaseShift == 0) {
                                            //If nbSat with Phase Shift is not indicated: all the satellites are affected for this Obs Type
                                        } else {
                                            satsPhaseShift = new String[nbSatPhaseShift];
                                            final int nbLinesSatPhaseShift = (nbSatPhaseShift + MAX_N_SAT_PHSHIFT_PER_LINE - 1) / MAX_N_SAT_PHSHIFT_PER_LINE;
                                            for (int j = 0; j < nbLinesSatPhaseShift; j++) {
                                                if (j > 0) {
                                                    line = reader.readLine(); //Next line
                                                    lineNumber++;
                                                }
                                                final int iMax = FastMath.min(MAX_N_SAT_PHSHIFT_PER_LINE, nbSatPhaseShift - j * MAX_N_SAT_PHSHIFT_PER_LINE);
                                                for (int i = 0; i < iMax; i++) {
                                                    satsPhaseShift[i + 10 * j] = parseString(line, 19 + 4 * i, 3);
                                                }
                                            }
                                        }
                                        phaseShiftCorrections.add(new PhaseShiftCorrection(satSystemPhaseShift,
                                                                                           phaseShiftTypeObs,
                                                                                           corrPhaseShift,
                                                                                           satsPhaseShift));
                                        inPhaseShift = true;
                                        break;
                                    case GLONASS_SLOT_FRQ_NB :
                                        //Not defined yet
                                        inGlonassSlot = true;
                                        break;
                                    case GLONASS_COD_PHS_BIS :
                                        //Not defined yet
                                        inGlonassCOD = true;
                                        break;
                                    case END_OF_HEADER :
                                        //We make sure that we have read all the mandatory fields inside the header of the Rinex
                                        if (!inRinexVersion || !inRunBy || !inMarkerName ||
                                            !inMarkerType || !inObserver || !inRecType || !inAntType ||
                                            !inAproxPos || !inAntDelta || !inTypesObs || !inFirstObs ||
                                            (formatVersion >= 3.01 && !inPhaseShift) ||
                                            (formatVersion >= 3.03 && (!inGlonassSlot || !inGlonassCOD))) {
                                            throw new OrekitException(OrekitMessages.INCOMPLETE_HEADER, name);
                                        }

                                        //Header information gathered
                                        observationsList = addHeader(new RinexHeader(formatVersion, satelliteSystem,
                                                                                     markerName, markerNumber, markerType,
                                                                                     observerName, agencyName, receiverNumber,
                                                                                     receiverType, receiverVersion, antennaNumber,
                                                                                     antennaType, approxPos, antHeight, eccentricities,
                                                                                     antRefPoint, obsCode, antPhaseCenter, antBSight,
                                                                                     antAzi, antZeroDir, centerMass, sigStrengthUnit,
                                                                                     interval, tFirstObs, tLastObs, clkOffset, listAppliedDCBs,
                                                                                     listAppliedPCVS, phaseShiftCorrections, leapSeconds,
                                                                                     leapSecondsFuture, leapSecondsWeekNum, leapSecondsDayNum));
                                        break;
                                    default :
                                        if (observationsList == null) {
                                            //There must be an error due to an unknown Label inside the Header
                                            throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                                      lineNumber, name, line);
                                        }
                                }
                            } else {
                                //If End of Header

                                //Start of a new Observation
                                rcvrClkOffset     =  0;
                                eventFlag         = -1;
                                nbSatObs          = -1;
                                tObs              = null;

                                //A line that starts with ">" correspond to a new observation epoch
                                if (parseString(line, 0, 1).equals(">")) {

                                    eventFlag = parseInt(line, 31, 1);
                                    //If eventFlag>1, we skip the corresponding lines to the next observation
                                    if (eventFlag != 0) {
                                        final int nbLinesSkip = parseInt(line, 32, 3);
                                        for (int i = 0; i < nbLinesSkip; i++) {
                                            line = reader.readLine();
                                            lineNumber++;
                                        }
                                    } else {

                                        tObs = new AbsoluteDate(parseInt(line, 2, 4),
                                                                parseInt(line, 6, 3),
                                                                parseInt(line, 9, 3),
                                                                parseInt(line, 12, 3),
                                                                parseInt(line, 15, 3),
                                                                parseDouble(line, 18, 11), timeScale);

                                        nbSatObs  = parseInt(line, 32, 3);
                                        //If the total number of satellites was indicated in the Header
                                        if (nbSat != -1 && nbSatObs > nbSat) {
                                            //we check that the number of Sat in the observation is consistent
                                            throw new OrekitException(OrekitMessages.INCONSISTENT_NUMBER_OF_SATS,
                                                                      lineNumber, name, nbSatObs, nbSat);
                                        }
                                        //Read the Receiver Clock offset, if present
                                        rcvrClkOffset = parseDouble(line, 41, 15);

                                        //For each one of the Satellites in this Observation
                                        for (int i = 0; i < nbSatObs; i++) {

                                            line = reader.readLine();
                                            lineNumber++;

                                            //We check that the Satellite type is consistent with Satellite System in the top of the file
                                            final SatelliteSystem satelliteSystemSat = SatelliteSystem.parseSatelliteSystem(parseString(line, 0, 1));
                                            if (!satelliteSystem.equals(SatelliteSystem.MIXED)) {
                                                if (!satelliteSystemSat.equals(satelliteSystem)) {
                                                    throw new OrekitException(OrekitMessages.INCONSISTENT_SATELLITE_SYSTEM,
                                                                              lineNumber, name, satelliteSystem, satelliteSystemSat);
                                                }
                                            }

                                            final int prn = parseInt(line, 1, 2);
                                            final int prnNumber;
                                            switch (satelliteSystemSat) {
                                                case GPS:
                                                case GLONASS:
                                                case GALILEO:
                                                case COMPASS:
                                                case IRNSS:
                                                    prnNumber = prn;
                                                    break;
                                                case QZSS:
                                                    prnNumber = prn + 192;
                                                    break;
                                                case SBAS:
                                                    prnNumber = prn + 100;
                                                    break;
                                                default:
                                                    // MIXED satellite system is not allowed here
                                                    throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                                              lineNumber, name, line);
                                            }
                                            final List<ObservationData> observationData = new ArrayList<>(nbSatObs);
                                            for (int j = 0; j < listTypeObs.get(satelliteSystemSat).size(); j++) {
                                                final RinexFrequency rf = listTypeObs.get(satelliteSystemSat).get(j);
                                                boolean scaleFactorFound = false;
                                                //We look for the lines of ScaledFactorCorrections that correspond to this SatSystem
                                                int k = 0;
                                                double value = parseDouble(line, 3 + j * 16, 14);
                                                while (k < scaleFactorCorrections.size() && !scaleFactorFound) {
                                                    if (scaleFactorCorrections.get(k).getSatelliteSystem().equals(satelliteSystemSat)) {
                                                        //We check if the next Observation Type to read needs to be scaled
                                                        if (scaleFactorCorrections.get(k).getTypesObsScaled().contains(rf)) {
                                                            value /= scaleFactorCorrections.get(k).getCorrection();
                                                            scaleFactorFound = true;
                                                        }
                                                    }
                                                    k++;
                                                }
                                                observationData.add(new ObservationData(rf,
                                                                                        value,
                                                                                        parseInt(line, 17 + j * 16, 1),
                                                                                        parseInt(line, 18 + j * 16, 1)));
                                            }
                                            observationsList.add(new ObservationDataSet(satelliteSystemSat, prnNumber, tObs, rcvrClkOffset,
                                                                                        observationData));

                                        }
                                    }
                                }
                            }
                        }
                        break;
                    default:
                        //If RINEX Version is neither 2 nor 3
                        throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, name);
                }
            }
        }


        /** Extract a string from a line.
         * @param line to parse
         * @param start start index of the string
         * @param length length of the string
         * @return parsed string
         */
        private String parseString(final String line, final int start, final int length) {
            if (line.length() > start) {
                return line.substring(start, FastMath.min(line.length(), start + length)).trim();
            } else {
                return null;
            }
        }

        /** Extract an integer from a line.
         * @param line to parse
         * @param start start index of the integer
         * @param length length of the integer
         * @return parsed integer
         */
        private int parseInt(final String line, final int start, final int length) {
            if (line.length() > start &&
                            !parseString(line, start, length).isEmpty()) {
                return Integer.parseInt(parseString(line, start, length));
            } else {
                return 0;
            }
        }

        /** Extract a double from a line.
         * @param line to parse
         * @param start start index of the real
         * @param length length of the real
         * @return parsed real
         */
        private double parseDouble(final String line, final int start, final int length) {
            if (line.length() > start &&
                            !parseString(line, start, length).isEmpty()) {
                return Double.parseDouble(parseString(line, start, length));
            } else {
                return 0.0;
            }
        }

        /** Phase Shift corrections.
         * Contains the phase shift corrections used to
         * generate phases consistent with respect to cycle shifts.
         */
        public class PhaseShiftCorrection {

            /** Satellite System. */
            private final SatelliteSystem satSystemPhaseShift;
            /** Carrier Phase Observation Code. */
            private final RinexFrequency typeObsPhaseShift;
            /** Phase Shift Corrections (cycles). */
            private final double phaseShiftCorrection;
            /** List of satellites involved. */
            private final String[] satsPhaseShift;

            /** Simple constructor.
             * @param satSystemPhaseShift Satellite System
             * @param typeObsPhaseShift Carrier Phase Observation Code
             * @param phaseShiftCorrection Phase Shift Corrections (cycles)
             * @param satsPhaseShift List of satellites involved
             */
            private PhaseShiftCorrection (final SatelliteSystem satSystemPhaseShift,
                                         final RinexFrequency typeObsPhaseShift,
                                         final double phaseShiftCorrection, final String[] satsPhaseShift) {
                this.satSystemPhaseShift = satSystemPhaseShift;
                this.typeObsPhaseShift = typeObsPhaseShift;
                this.phaseShiftCorrection = phaseShiftCorrection;
                this.satsPhaseShift = satsPhaseShift;
            }

            /** Get the Satellite System.
             * @return Satellite System.
             */
            public SatelliteSystem getSatelliteSystem() {
                return satSystemPhaseShift;
            }
            /** Get the Carrier Phase Observation Code.
             * @return Carrier Phase Observation Code.
             */
            public RinexFrequency getTypeObs() {
                return typeObsPhaseShift;
            }
            /** Get the Phase Shift Corrections.
             * @return Phase Shift Corrections (cycles)
             */
            public double getCorrection() {
                return phaseShiftCorrection;
            }
            /** Get the list of satellites involved.
             * @return List of satellites involved (if null, all the sats are involved)
             */
            public String[] getSatsCorrected() {
                //If empty, all the satellites of this constellation are affected for this Observation type
                return satsPhaseShift;
            }
        }

        /** Scale Factor to be applied.
         * Contains the scale factors of 10 applied to the data before
         * being stored into the RINEX file.
         */
        public class ScaleFactorCorrection {

            /** Satellite System. */
            private final SatelliteSystem satSystemScaleFactor;
            /** List of Observations types that have been scaled. */
            private final List<RinexFrequency> typesObsScaleFactor;
            /** Factor to divide stored observations with before use. */
            private final double scaleFactor;

            /** Simple constructor.
             * @param satSystemScaleFactor Satellite System
             * @param scaleFactor Factor to divide stored observations (1,10,100,1000)
             * @param typesObsScaleFactor List of Observations types that have been scaled
             */
            private ScaleFactorCorrection (final SatelliteSystem satSystemScaleFactor,
                                          final double scaleFactor,
                                          final List<RinexFrequency> typesObsScaleFactor) {
                this.satSystemScaleFactor = satSystemScaleFactor;
                this.scaleFactor = scaleFactor;
                this.typesObsScaleFactor = typesObsScaleFactor;
            }
            /** Get the Satellite System.
             * @return Satellite System
             */
            public SatelliteSystem getSatelliteSystem() {
                return satSystemScaleFactor;
            }
            /** Get the Scale Factor.
             * @return Scale Factor
             */
            public double getCorrection() {
                return scaleFactor;
            }
            /** Get the list of Observation Types scaled.
             * @return List of Observation types scaled
             */
            public List<RinexFrequency> getTypesObsScaled() {
                return typesObsScaleFactor;
            }
        }

        /** Corrections of Differential Code Biases (DCBs) applied.
         * Contains information on the programs used to correct the observations
         * in RINEX files for differential code biases.
         */
        public class AppliedDCBs {

            /** Program name used to apply differential code bias corrections. */
            private final String progDCBs;
            /** Source of corrections (URL). */
            private final String sourceDCBs;

            /** Simple constructor.
             * @param progDCBsG Program name used to apply DCBs
             * @param sourceDCBsG Source of corrections (URL)
             */
            private AppliedDCBs (final String progDCBsG, final String sourceDCBsG) {
                this.progDCBs = progDCBsG;
                this.sourceDCBs = sourceDCBsG;
            }
            /** Get the program name used to apply DCBs.
             * @return  Program name used to apply DCBs
             */
            public String getProgDCBs() {
                return progDCBs;
            }
            /** Get the source of corrections.
             * @return Source of corrections (URL)
             */
            public String getSourceDCBs() {
                return sourceDCBs;
            }

        }
        /** Corrections of antenna phase center variations (PCVs) applied.
         * Contains information on the programs used to correct the observations
         * in RINEX files for antenna phase center variations.
         */
        public class AppliedPCVS {

            /** Program name used to antenna center variation corrections. */
            private final String progPCVS;
            /** Source of corrections (URL). */
            private final String sourcePCVS;

            /** Simple constructor.
             * @param progPCVS Program name used for PCVs
             * @param sourcePCVS Source of corrections (URL)
             */
            private AppliedPCVS (final String progPCVS, final String sourcePCVS) {
                this.progPCVS = progPCVS;
                this.sourcePCVS = sourcePCVS;
            }
            /** Get the program name used to apply PCVs.
             * @return  Program name used to apply PCVs
             */
            public String getProgPCVS() {
                return progPCVS;
            }
            /** Get the source of corrections.
             * @return Source of corrections (URL)
             */
            public String getSourcePCVS() {
                return sourcePCVS;
            }

        }
    }

}