package com.termux.api.apis;

import android.content.Intent;
import android.hardware.SensorManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for https://github.com/termux/termux-api/issues/902
 */
public class SensorAPICleanupTest {

    private SensorManager sensorManager;

    @Before
    public void setUp() {
        sensorManager = mock(SensorManager.class);
        SensorAPI.SensorReaderService.sensorManager = sensorManager;
        SensorAPI.SensorReaderService.outputWriter = null;
        SensorAPI.SensorReaderService.listenerRegistered = false;
    }

    @After
    public void tearDown() {
        SensorAPI.SensorReaderService.sensorManager = null;
        SensorAPI.SensorReaderService.outputWriter = null;
        SensorAPI.SensorReaderService.listenerRegistered = false;
    }

    @Test
    public void cleanupUnregistersListenerEvenWhenWriterIsGone() {
        // State after the output socket broke on the old code: writer nulled, listener still registered.
        SensorAPI.SensorReaderService.listenerRegistered = true;

        SensorAPI.SensorCommandResult result = SensorAPI.SensorReaderService.cleanupHandler
                .handle(sensorManager, null, null);

        verify(sensorManager).unregisterListener(SensorAPI.SensorReaderService.sensorEventListener);
        assertEquals("Sensor cleanup successful!", result.message);
        assertFalse(SensorAPI.SensorReaderService.listenerRegistered);
    }

    @Test
    public void cleanupWithNothingActiveIsUnnecessary() {
        SensorAPI.SensorCommandResult result = SensorAPI.SensorReaderService.cleanupHandler
                .handle(sensorManager, null, null);

        assertEquals("Sensor cleanup unnecessary", result.message);
    }

    @Test
    public void writerErrorReleasesSensors() {
        Intent intent = mock(Intent.class);
        when(intent.getStringExtra(anyString())).thenReturn("socket");
        when(intent.getIntExtra(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        SensorAPI.SensorReaderService.SensorOutputWriter writer =
                SensorAPI.SensorReaderService.createSensorOutputWriter(intent);
        SensorAPI.SensorReaderService.listenerRegistered = true;

        // Simulate the client being killed: writing to the output socket fails.
        writer.errorListener.onError(new IOException("Broken pipe"));

        verify(sensorManager).unregisterListener(SensorAPI.SensorReaderService.sensorEventListener);
        assertNull(SensorAPI.SensorReaderService.outputWriter);
        assertFalse(SensorAPI.SensorReaderService.listenerRegistered);
        assertFalse(writer.isRunning());
    }

    @Test
    public void staleWriterErrorDoesNotTouchNewStream() {
        Intent intent = mock(Intent.class);
        when(intent.getIntExtra(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        SensorAPI.SensorReaderService.SensorOutputWriter oldWriter =
                SensorAPI.SensorReaderService.createSensorOutputWriter(intent);
        SensorAPI.SensorReaderService.SensorOutputWriter newWriter =
                SensorAPI.SensorReaderService.createSensorOutputWriter(intent);
        SensorAPI.SensorReaderService.listenerRegistered = true;

        oldWriter.errorListener.onError(new IOException("Broken pipe"));

        verify(sensorManager, never()).unregisterListener(eq(SensorAPI.SensorReaderService.sensorEventListener));
        assertEquals(newWriter, SensorAPI.SensorReaderService.outputWriter);
    }
}
