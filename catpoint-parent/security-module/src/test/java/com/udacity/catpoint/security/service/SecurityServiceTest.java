package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {
    private SecurityService securityService;
    private Sensor sensor;

    @Mock
    private ImageService imageService;
    @Mock
    private SecurityRepository securityRepository;


    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor("temp", SensorType.DOOR);
    }


    /**
     * 1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
     */
    @Test
    public void whenSystemArmedWithSensorActivated_alarmStatusSetToPending()
    {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME, ArmingStatus.ARMED_AWAY);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    /**
     * 2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
     */
    @Test
    public void whenSystemArmedAndSensorActivatedWithPendingState_alarmStatusSetToAlarm()
    {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME, ArmingStatus.ARMED_AWAY);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * 3. If pending alarm and all sensors are inactive, return to no alarm state.
     */
    @Test
    public void whenPendingAlarmWithInactivatedSensors_alarmStatusSetToNoAlarm()
    {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * 4. If alarm is active, change in sensor state should not affect the alarm state.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void activeAlarmWithSensorStateChange_alarmStatusStayTheSame(boolean sensorState)
    {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, sensorState);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    /**
     * 5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.
     */
    @Test
    public void pendingAlarmWithActivatedActiveSensor_alarmStatusSetToAlarm()
    {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * 6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
     */
    @Test
    public void deactivatedInactiveSensor_alarmStatusStayTheSame()
    {
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    /**
     * 7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
     */
    @Test
    public void armedHomeWithCatDetected_alarmStatusSetToAlarm()
    {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * 8. If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
     */
    @Test
    public void noCatDetectedWithInactiveSensors_alarmStatusSetToNoAlarm()
    {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            sensors.add(new Sensor("temp" + i, SensorType.MOTION));
        }
        sensors.forEach(s -> s.setActive(false));
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * 9. If the system is disarmed, set the status to no alarm.
     */
    @Test
    public void whenSystemDisarmed_alarmStatusSetToNoAlarm()
    {
        when(securityService.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME, ArmingStatus.ARMED_AWAY);
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * 10. If the system is armed, reset all sensors to inactive.
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    public void whenSystemArmed_allSensorStatusResetToInactive(ArmingStatus status)
    {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            sensors.add(new Sensor("temp" + i, SensorType.MOTION));
        }
        sensors.forEach(s -> s.setActive(true));
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(status);
        securityService.getSensors().forEach(s -> assertFalse(s.getActive()));
    }

    /**
     * 11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
     */
    @Test
    public void whenArmedHomeWithCatDetected_alarmStatusSetToAlarm()
    {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

}
