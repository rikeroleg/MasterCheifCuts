package com.masterchefcuts.services;

import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceImplTest {

    @Mock ParticipantRepo participantRepo;
    @InjectMocks ParticipantServiceImpl service;

    @Test
    void addParticipant_delegatesToRepoSaveAndReturnsResult() {
        Participant participant = new Participant();
        participant.setId("user-1");

        when(participantRepo.save(participant)).thenReturn(participant);

        Participant result = service.addParticipant(participant);

        assertThat(result).isSameAs(participant);
        verify(participantRepo).save(participant);
    }

    @Test
    void findById_existingId_returnsParticipant() {
        Participant participant = new Participant();
        participant.setId("user-1");

        when(participantRepo.findById("user-1")).thenReturn(Optional.of(participant));

        Participant result = service.findById("user-1");

        assertThat(result).isSameAs(participant);
    }

    @Test
    void findById_unknownId_throwsRuntimeException() {
        when(participantRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Participant not Found");
    }
}
