/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS.
 */

package org.forgerock.openig.filter;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.regex.Pattern;

import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.MessageType;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.regex.PatternTemplate;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class EntityExtractFilterTest {

    @Mock
    private Handler terminalHandler;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEntityExtractionFromRequestWithTemplates() throws Exception {
        EntityExtractFilter filter = new EntityExtractFilter(MessageType.REQUEST, new Expression("${exchange.result}"));
        filter.getExtractor().getPatterns().put("hello", Pattern.compile("Hello(.*)"));
        filter.getExtractor().getPatterns().put("none", Pattern.compile("Cannot match"));
        filter.getExtractor().getTemplates().put("hello", new PatternTemplate("$1"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity("Hello OpenIG");

        filter.filter(exchange, terminalHandler);

        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) exchange.get("result");
        assertThat(results).containsOnly(
                entry("hello", " OpenIG"),
                entry("none", null));
        verify(terminalHandler).handle(exchange);
    }

    @Test
    public void testEntityExtractionFromRequestWithNoTemplates() throws Exception {
        EntityExtractFilter filter = new EntityExtractFilter(MessageType.REQUEST, new Expression("${exchange.result}"));
        filter.getExtractor().getPatterns().put("hello", Pattern.compile("Hello(.*)"));
        filter.getExtractor().getPatterns().put("none", Pattern.compile("Cannot match"));

        Exchange exchange = new Exchange();
        exchange.request = new Request();
        exchange.request.setEntity("Hello OpenIG");

        filter.filter(exchange, terminalHandler);

        // The entry has a non-null value if it matches or a null value if it does not match
        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) exchange.get("result");
        assertThat(results).containsOnly(
                entry("hello", "Hello OpenIG"),
                entry("none", null));
        verify(terminalHandler).handle(exchange);
    }

    @Test
    public void testResultMapIsEmptyWhenThereIsNoEntity() throws Exception {
        EntityExtractFilter filter = new EntityExtractFilter(MessageType.RESPONSE,
                                                             new Expression("${exchange.result}"));
        filter.getExtractor().getPatterns().put("hello", Pattern.compile("Hello(.*)"));

        Exchange exchange = new Exchange();
        exchange.response = new Response();
        exchange.response.setEntity((String) null);

        filter.filter(exchange, terminalHandler);

        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) exchange.get("result");
        assertThat(results).containsOnly(entry("hello", null));
        verify(terminalHandler).handle(exchange);
    }
}
