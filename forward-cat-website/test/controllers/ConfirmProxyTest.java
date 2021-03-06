package controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardcat.common.ProxyMail;
import com.google.inject.AbstractModule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import play.mvc.Result;
import play.test.FakeRequest;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.io.IOException;

import static models.ControllerUtils.getHash;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.test.Helpers.*;

@RunWith(MockitoJUnitRunner.class)
public class ConfirmProxyTest extends PlayTest {

    String proxyMail = "test@forward.cat";
    String proxyData = "{\"ua\":\"my_address@mail.com\",\"ts\":\"2013-01-27T01:58:53.874+01:00\",\"ex\":\"2013-02-01T01:58:53.874+01:00\",\"ac\":false}";
    String proxyHash;

    @Mock JedisPool jedisPool;
    @Mock Jedis jedis;
    @Mock Pipeline pipeline;
    @Mock Response<Long> expireResponse;

    @Override
    public AbstractModule getModule() throws IOException {
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.get("p:" + proxyMail)).thenReturn(proxyData);
        when(jedis.pipelined()).thenReturn(pipeline);
        when(pipeline.expire(anyString(), anyInt())).thenReturn(expireResponse);
        when(expireResponse.get()).thenReturn(1L);

        ProxyMail proxy = new ObjectMapper().readValue(proxyData, ProxyMail.class);
        proxyHash = getHash(proxy);

        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(JedisPool.class).toInstance(jedisPool);
            }
        };
    }

    @Test
    public void proxyMailMissing_sendBadRequest() throws Exception {
        Result route = route(request(null, proxyHash));
        assertThat(status(route), is(BAD_REQUEST));
    }

    @Test
    public void hashMissing_sendBadRequest() throws Exception {
        Result route = route(request(proxyMail, null));
        assertThat(status(route), is(BAD_REQUEST));
    }

    @Test
    public void proxyNonExisting_sendBadRequest() throws Exception {
        Result route = route(request("nonValid@forward.cat", proxyHash));
        assertThat(status(route), is(BAD_REQUEST));
    }

    @Test
    public void invalidHash_sendBadRequest() throws Exception {
        Result route = route(request(proxyMail, "wrongHash"));
        assertThat(status(route), is(BAD_REQUEST));
    }

    @Test
    public void everythingFine_sendConfirmationPage() throws Exception {
        Result route = route(request(proxyMail, proxyHash));
        assertThat(status(route), is(OK));
    }

    private FakeRequest request(String proxy, String hash) {
        if (proxy == null) {
            return fakeRequest(GET, "/confirm?h=" + hash);
        } else if (hash == null) {
            return fakeRequest(GET, "/confirm?p=" + proxy);
        }
        return fakeRequest(GET, "/confirm?p=" + proxy + "&h=" + hash);
    }
}
