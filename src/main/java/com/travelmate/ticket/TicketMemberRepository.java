package com.travelmate.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMemberRepository extends JpaRepository<TicketMember, Long> {

    List<TicketMember> findByTicketId(Long ticketId);

    List<TicketMember> findByTicketIdIn(List<Long> ticketIds);

    List<TicketMember> findByMemberId(Long memberId);

    void deleteByTicketId(Long ticketId);
}
