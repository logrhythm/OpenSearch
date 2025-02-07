/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.action.admin.cluster.health;

import org.opensearch.LegacyESVersion;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.common.Priority;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.VersionUtils;

import java.util.Locale;

import static org.opensearch.test.VersionUtils.getPreviousVersion;
import static org.opensearch.test.VersionUtils.randomVersionBetween;
import static org.hamcrest.core.IsEqual.equalTo;

public class ClusterHealthRequestTests extends OpenSearchTestCase {

    public void testSerialize() throws Exception {
        final ClusterHealthRequest originalRequest = randomRequest();
        final ClusterHealthRequest cloneRequest;
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            originalRequest.writeTo(out);
            try (StreamInput in = out.bytes().streamInput()) {
                cloneRequest = new ClusterHealthRequest(in);
            }
        }
        assertThat(cloneRequest.waitForStatus(), equalTo(originalRequest.waitForStatus()));
        assertThat(cloneRequest.waitForNodes(), equalTo(originalRequest.waitForNodes()));
        assertThat(cloneRequest.waitForNoInitializingShards(), equalTo(originalRequest.waitForNoInitializingShards()));
        assertThat(cloneRequest.waitForNoRelocatingShards(), equalTo(originalRequest.waitForNoRelocatingShards()));
        assertThat(cloneRequest.waitForActiveShards(), equalTo(originalRequest.waitForActiveShards()));
        assertThat(cloneRequest.waitForEvents(), equalTo(originalRequest.waitForEvents()));
        assertIndicesEquals(cloneRequest.indices(), originalRequest.indices());
        assertThat(cloneRequest.indicesOptions(), equalTo(originalRequest.indicesOptions()));
    }

    public void testRequestReturnsHiddenIndicesByDefault() {
        final ClusterHealthRequest defaultRequest = new ClusterHealthRequest();
        assertTrue(defaultRequest.indicesOptions().expandWildcardsHidden());
    }

    public void testBwcSerialization() throws Exception {
        for (int runs = 0; runs < randomIntBetween(5, 20); runs++) {
            // Generate a random cluster health request in version < 7.2.0 and serializes it
            final BytesStreamOutput out = new BytesStreamOutput();
            out.setVersion(randomVersionBetween(random(), VersionUtils.getFirstVersion(), getPreviousVersion(LegacyESVersion.V_7_2_0)));

            final ClusterHealthRequest expected = randomRequest();
            {
                expected.getParentTask().writeTo(out);
                out.writeTimeValue(expected.masterNodeTimeout());
                out.writeBoolean(expected.local());
                if (expected.indices() == null) {
                    out.writeVInt(0);
                } else {
                    out.writeVInt(expected.indices().length);
                    for (String index : expected.indices()) {
                        out.writeString(index);
                    }
                }
                out.writeTimeValue(expected.timeout());
                if (expected.waitForStatus() == null) {
                    out.writeBoolean(false);
                } else {
                    out.writeBoolean(true);
                    out.writeByte(expected.waitForStatus().value());
                }
                out.writeBoolean(expected.waitForNoRelocatingShards());
                expected.waitForActiveShards().writeTo(out);
                out.writeString(expected.waitForNodes());
                if (expected.waitForEvents() == null) {
                    out.writeBoolean(false);
                } else {
                    out.writeBoolean(true);
                    Priority.writeTo(expected.waitForEvents(), out);
                }
                out.writeBoolean(expected.waitForNoInitializingShards());
            }

            // Deserialize and check the cluster health request
            final StreamInput in = out.bytes().streamInput();
            in.setVersion(out.getVersion());
            final ClusterHealthRequest actual = new ClusterHealthRequest(in);

            assertThat(actual.waitForStatus(), equalTo(expected.waitForStatus()));
            assertThat(actual.waitForNodes(), equalTo(expected.waitForNodes()));
            assertThat(actual.waitForNoInitializingShards(), equalTo(expected.waitForNoInitializingShards()));
            assertThat(actual.waitForNoRelocatingShards(), equalTo(expected.waitForNoRelocatingShards()));
            assertThat(actual.waitForActiveShards(), equalTo(expected.waitForActiveShards()));
            assertThat(actual.waitForEvents(), equalTo(expected.waitForEvents()));
            assertIndicesEquals(actual.indices(), expected.indices());
            assertThat(actual.indicesOptions(), equalTo(IndicesOptions.lenientExpandOpen()));
        }

        for (int runs = 0; runs < randomIntBetween(5, 20); runs++) {
            // Generate a random cluster health request in current version
            final ClusterHealthRequest expected = randomRequest();

            // Serialize to node in version < 7.2.0
            final BytesStreamOutput out = new BytesStreamOutput();
            out.setVersion(randomVersionBetween(random(), VersionUtils.getFirstVersion(), getPreviousVersion(LegacyESVersion.V_7_2_0)));
            expected.writeTo(out);

            // Deserialize and check the cluster health request
            final StreamInput in = out.bytes().streamInput();
            in.setVersion(out.getVersion());
            final ClusterHealthRequest actual = new ClusterHealthRequest(in);

            assertThat(actual.waitForStatus(), equalTo(expected.waitForStatus()));
            assertThat(actual.waitForNodes(), equalTo(expected.waitForNodes()));
            assertThat(actual.waitForNoInitializingShards(), equalTo(expected.waitForNoInitializingShards()));
            assertThat(actual.waitForNoRelocatingShards(), equalTo(expected.waitForNoRelocatingShards()));
            assertThat(actual.waitForActiveShards(), equalTo(expected.waitForActiveShards()));
            assertThat(actual.waitForEvents(), equalTo(expected.waitForEvents()));
            assertIndicesEquals(actual.indices(), expected.indices());
            assertThat(actual.indicesOptions(), equalTo(IndicesOptions.lenientExpandOpen()));
        }
    }

    private ClusterHealthRequest randomRequest() {
        ClusterHealthRequest request = new ClusterHealthRequest();
        request.waitForStatus(randomFrom(ClusterHealthStatus.values()));
        request.waitForNodes(randomFrom("", "<", "<=", ">", ">=") + between(0, 1000));
        request.waitForNoInitializingShards(randomBoolean());
        request.waitForNoRelocatingShards(randomBoolean());
        request.waitForActiveShards(randomIntBetween(0, 10));
        request.waitForEvents(randomFrom(Priority.values()));
        if (randomBoolean()) {
            final String[] indices = new String[randomIntBetween(1, 10)];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = randomAlphaOfLength(10).toLowerCase(Locale.ROOT);
            }
            request.indices(indices);
        }
        if (randomBoolean()) {
            request.indicesOptions(IndicesOptions.fromOptions(randomBoolean(), randomBoolean(), randomBoolean(), randomBoolean()));
        }
        return request;
    }

    private static void assertIndicesEquals(final String[] actual, final String[] expected) {
        // null indices in ClusterHealthRequest is deserialized as empty string array
        assertArrayEquals(expected != null ? expected : Strings.EMPTY_ARRAY, actual);
    }
}
