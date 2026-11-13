package io.miragon.miravelo.leasing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderBikeServiceTest {

    private final OrderBikeService underTest = new OrderBikeService();

    @Test
    void placesANewOrderForEveryApplication() {
        String firstOrder = underTest.orderBike("APP-1");
        String secondOrder = underTest.orderBike("APP-2");

        assertThat(firstOrder).startsWith("ORDER-");
        assertThat(secondOrder).startsWith("ORDER-").isNotEqualTo(firstOrder);
    }
}
