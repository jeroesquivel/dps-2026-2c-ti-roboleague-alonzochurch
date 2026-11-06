package com.dps.roboleague.infrastructure.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envuelve cada caso de uso en una transacción, sin que el núcleo sepa que existen. Cada caso de uso es una unidad
 * de trabajo: si lanza una excepción del dominio (un conflicto, una regla violada) se deshace todo lo que había
 * guardado hasta ahí, y si termina bien se confirma de una vez.
 *
 * <p>Es un decorador de los puertos de entrada: los controladores y los demás clientes reciben el proxy sin
 * enterarse. El núcleo queda libre de {@code @Transactional}.
 */
public final class TransactionalUseCases implements BeanPostProcessor {

    private static final String USE_CASE_PACKAGE = "com.dps.roboleague.";
    private static final String USE_CASE_SUFFIX = "UseCase";

    private final ObjectProvider<PlatformTransactionManager> transactionManager;

    public TransactionalUseCases(ObjectProvider<PlatformTransactionManager> transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> type = bean.getClass();
        if (!type.getName().startsWith(USE_CASE_PACKAGE) || !type.getSimpleName().endsWith(USE_CASE_SUFFIX)
                || type.getInterfaces().length == 0) {
            return bean;
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager.getObject());
        return Proxy.newProxyInstance(type.getClassLoader(), type.getInterfaces(), (proxy, method, args) ->
                transaction.execute(status -> {
                    try {
                        return method.invoke(bean, args);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        throw new UndeclaredThrowableException(e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new UndeclaredThrowableException(e);
                    }
                }));
    }
}
