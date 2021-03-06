package controllers;

import com.forwardcat.common.ProxyMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.Json;
import play.mvc.Controller;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Optional;

import static models.JedisHelper.returnJedisIfNotNull;
import static models.JedisHelper.returnJedisOnException;

abstract class AbstractController extends Controller {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractController.class.getName());
    private final JedisPool jedisPool;

    protected AbstractController(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Returns the {@link ProxyMail} linked to the given proxy key
     */
    protected Optional<ProxyMail> getProxy(String proxyKey) {
        return dbFunction(jedis -> {
            String proxyString = jedis.get(proxyKey);
            if (proxyString != null) {
                try {
                    return Json.fromJson(Json.parse(proxyString), ProxyMail.class);
                } catch (Exception ex) {
                    LOGGER.error("Error while parsing proxy: " + proxyString, ex);
                }
            }
            return null;
        });
    }

    protected <T> Optional<T> dbFunction(DbFunction<T> function) {
        Jedis jedis = null;
        Optional<T> retValue;
        try {
            jedis = jedisPool.getResource();
            retValue = Optional.ofNullable(function.apply(jedis));
        } catch (Exception ex) {
            LOGGER.error("Error while connecting to Redis", ex);
            returnJedisOnException(jedisPool, jedis, ex);
            jedis = null;
            retValue = Optional.empty();
        } finally {
            returnJedisIfNotNull(jedisPool, jedis);
        }
        return retValue;
    }

    protected void dbStatement(DbConsumer user) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            user.accept(jedis);
        } catch (Exception ex) {
            LOGGER.error("Error while connecting to Redis", ex);
            returnJedisOnException(jedisPool, jedis, ex);
            jedis = null;
        } finally {
            returnJedisIfNotNull(jedisPool, jedis);
        }
    }

    @FunctionalInterface
    protected static interface DbFunction<T> {
        public T apply(Jedis jedis) throws Exception;
    }

    @FunctionalInterface
    protected static interface DbConsumer {
        public void accept(Jedis jedis) throws Exception;
    }

    public String toJsonString(Object object) {
        try {
            return Json.stringify(Json.toJson(object));
        } catch (Exception ex) {
            LOGGER.error("Error while converting object to JSON string: " + object, ex); // Should never happen
        }
        return null;
    }
}
