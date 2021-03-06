/*
 * Copyright (C) 2014-2018 D3X Systems - All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.d3x.morpheus.wb;

import java.time.Month;
import java.util.Set;
import java.util.stream.Collectors;

import com.d3x.morpheus.frame.DataFrame;
import com.d3x.morpheus.util.Asserts;
import com.d3x.morpheus.wb.climate.WBClimate;
import com.d3x.morpheus.wb.climate.WBClimateKey;
import com.d3x.morpheus.wb.source.WBClimateSource;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A unit test for the World Bank Climate API
 *
 * @see <a href="https://datahelpdesk.worldbank.org/knowledgebase/articles/902061-climate-data-api">World Bank Climate API</a>
 *
 * @author  Xavier Witdouck
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 */
public class WBClimateTest {

    private WBClimateSource source = new WBClimateSource();

    @DataProvider(name="countries")
    public Object[][] countryCodes() {
        return new Object[][] {{"USA"}, {"AUS"}, {"GBR"}, {"DEU"}, {"ZAF"}, {"GHA"}, {"BRA"}};
    }


    @Test(dataProvider = "countries", enabled = false)
    public void testSingleModelTemperatureQuery(String isoCode) {
        for (WBClimate.GCM gcm : WBClimate.GCM.values()) {
            final DataFrame<WBClimateKey,Month> frame = source.read(options -> {
                options.setVariable(WBClimate.Variable.PRECIPITATION);
                options.setCountryCode(isoCode);
                options.setGcm(gcm);
            });
            frame.out().print();
            Asserts.assertTrue(frame.rowCount() > 0);
            Assert.assertEquals(frame.colCount(), 12);
            frame.rows().forEach(row -> {
                Assert.assertEquals(row.key().getGcm(), gcm);
                Assert.assertEquals(row.key().getVariable(), WBClimate.Variable.PRECIPITATION);
                Assert.assertTrue(!row.hasNulls(), "All values are set for " + row.key());
            });
        }
    }


    @Test(dataProvider = "countries", enabled = false)
    public void testSingleModelPrecipitationQuery(String isoCode) {
        for (WBClimate.GCM gcm : WBClimate.GCM.values()) {
            final DataFrame<WBClimateKey,Month> frame = source.read(options -> {
                options.setVariable(WBClimate.Variable.PRECIPITATION);
                options.setCountryCode(isoCode);
                options.setGcm(gcm);
            });
            frame.out().print();
            Asserts.assertTrue(frame.rowCount() > 0);
            Assert.assertEquals(frame.colCount(), 12);
            frame.rows().forEach(row -> {
                Assert.assertEquals(row.key().getGcm(), gcm);
                Assert.assertEquals(row.key().getVariable(), WBClimate.Variable.PRECIPITATION);
                Assert.assertTrue(!row.hasNulls(), "All values are set for " + row.key());
            });
        }
    }


    @Test(dataProvider = "countries", enabled = false)
    public void testAllModels(String isoCode) {
        final DataFrame<WBClimateKey,Month> frame = source.read(options -> {
            options.setVariable(WBClimate.Variable.PRECIPITATION);
            options.setCountryCode(isoCode);
        });
        frame.out().print();
        final Set<WBClimate.GCM> gcmSet = frame.rows().stream().map(row -> row.key().getGcm()).collect(Collectors.toSet());
        Assert.assertEquals(gcmSet.size(), 15);
        for (WBClimate.GCM gcm : WBClimate.GCM.values()) {
            Assert.assertTrue(gcmSet.contains(gcm), "Data for GCM" + gcm + " contained in results");
        }
    }


    @Test(dataProvider = "countries", enabled = false)
    public void testEmissionModel(String isoCode) {
        for (WBClimate.SRES sres : WBClimate.SRES.values()) {
            final DataFrame<WBClimateKey,Month> frame = source.read(options -> {
                options.setVariable(WBClimate.Variable.PRECIPITATION);
                options.setSres(sres);
                options.setGcm(WBClimate.GCM.BCM_2_0);
                options.setCountryCode(isoCode);
            });
            frame.out().print();
            Asserts.assertTrue(frame.rowCount() > 0);
            Assert.assertEquals(frame.colCount(), 12);
            frame.rows().forEach(row -> {
                Assert.assertEquals(row.key().getGcm(), WBClimate.GCM.BCM_2_0);
                Assert.assertEquals(row.key().getVariable(), WBClimate.Variable.PRECIPITATION);
                Assert.assertEquals(row.key().getScenario(), sres);
                Assert.assertTrue(!row.hasNulls(), "All values are set for " + row.key());
            });
        }
    }


}
