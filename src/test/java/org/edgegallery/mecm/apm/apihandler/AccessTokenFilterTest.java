/*
 *    Copyright 2020-2021 Huawei Technologies Co., Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.edgegallery.mecm.apm.apihandler;

import org.edgegallery.mecm.apm.apihandler.access.AccessTokenFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
@AutoConfigureMockMvc
public class AccessTokenFilterTest {

        public static final String HEALTH_URI = "/apm/v1/health";

        AccessTokenFilter filter;
        HttpServletRequest mockReq;
        HttpServletResponse mockResp;
        FilterChain mockFilterChain;
        FilterConfig mockFilterConfig;
        OAuth2AccessToken oAuth2AccessToken;

        @Before
        public void onStartup() {
            filter = new AccessTokenFilter();
            mockFilterChain = mock(FilterChain.class);
            mockFilterConfig = mock(FilterConfig.class);
            oAuth2AccessToken = mock(OAuth2AccessToken.class);
        }

        @Test
        public void testDoFilter() throws ServletException, IOException {
            mockReq = mock(HttpServletRequest.class);
            mockResp = mock(HttpServletResponse.class);

            Mockito.when(mockReq.getRequestURI()).thenReturn(HEALTH_URI);
            BufferedReader br = new BufferedReader(new StringReader("test"));
       //     Mockito.when(mockReq.getReader()).thenReturn(br);

            filter.doFilter(mockReq, mockResp, mockFilterChain);
        }

        @Test(expected = Exception.class)
        public void testDoFilterException() throws IOException, ServletException {
            mockReq = mock(HttpServletRequest.class);
            mockResp = mock(HttpServletResponse.class);
            filter.doFilter(mockReq, mockResp, mockFilterChain);
            doThrow(new Exception("Error occurred because of : Access token is empty."))
                    .when(filter);
        }
}

