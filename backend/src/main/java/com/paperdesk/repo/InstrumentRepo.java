package com.paperdesk.repo;

import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstrumentRepo extends JpaRepository<Instrument, Long> {
    List<Instrument> findBySessionIdAndActiveTrue(Long sessionId);
    List<Instrument> findBySessionIdAndInstrumentTypeAndActiveTrue(Long sessionId, InstrumentType type);
    List<Instrument> findBySessionIdAndUnderlyingIdAndInstrumentTypeAndActiveTrue(Long sessionId, Long underlyingId, InstrumentType type);
    List<Instrument> findBySessionIdAndExpiryDateLessThanEqualAndActiveTrue(Long sessionId, LocalDate date);
    Optional<Instrument> findBySessionIdAndSymbol(Long sessionId, String symbol);
}
