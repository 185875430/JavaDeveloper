package com.junyu.lesson45.first;

/**
 * @author 俊语
 * @date 2021/4/14 下午11:59
 */
public class Demo {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("beans.xml");
        RateLimiter rateLimiter = (RateLimiter) applicationContext.getBean("rateLimiter");
        rateLimiter.test();
        //...
    }
}
