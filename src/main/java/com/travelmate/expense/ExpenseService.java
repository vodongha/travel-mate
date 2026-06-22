package com.travelmate.expense;

import com.travelmate.common.entity.Category;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import com.travelmate.common.money.MoneyService;
import com.travelmate.common.money.RateResolver;
import com.travelmate.expense.dto.CreateExpenseRequest;
import com.travelmate.expense.dto.ExpenseResponse;
import com.travelmate.expense.dto.ExpenseResponse.ShareView;
import com.travelmate.expense.dto.ExpenseShareInput;
import com.travelmate.expense.dto.UpdateExpenseRequest;
import com.travelmate.accommodation.Accommodation;
import com.travelmate.accommodation.AccommodationRepository;
import com.travelmate.place.Place;
import com.travelmate.place.PlaceRepository;
import com.travelmate.place.PlaceService;
import com.travelmate.timeline.Event;
import com.travelmate.timeline.EventRepository;
import com.travelmate.transport.Transport;
import com.travelmate.transport.TransportRepository;
import com.travelmate.trip.MemberRole;
import com.travelmate.trip.Trip;
import com.travelmate.trip.TripAccessGuard;
import com.travelmate.trip.TripMember;
import com.travelmate.trip.TripMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Expense CRUD with multi-currency snapshot + split (SPEC §7 Modules 9, 11). Rate snapshotting and
 * rounding go through {@link MoneyService}; the division goes through {@link ExpenseSplitter} (integer
 * minor units). All access is via {@link TripAccessGuard}.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository shareRepository;
    private final TripMemberRepository tripMemberRepository;
    private final PlaceRepository placeRepository;
    private final PlaceService placeService;
    private final EventRepository eventRepository;
    private final TransportRepository transportRepository;
    private final AccommodationRepository accommodationRepository;
    private final TripAccessGuard guard;
    private final MoneyService moneyService;
    private final RateResolver rateResolver;
    private final ExpenseSplitter splitter;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseShareRepository shareRepository,
                          TripMemberRepository tripMemberRepository,
                          PlaceRepository placeRepository,
                          PlaceService placeService,
                          EventRepository eventRepository,
                          TransportRepository transportRepository,
                          AccommodationRepository accommodationRepository,
                          TripAccessGuard guard,
                          MoneyService moneyService,
                          RateResolver rateResolver,
                          ExpenseSplitter splitter) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.placeRepository = placeRepository;
        this.placeService = placeService;
        this.eventRepository = eventRepository;
        this.transportRepository = transportRepository;
        this.accommodationRepository = accommodationRepository;
        this.guard = guard;
        this.moneyService = moneyService;
        this.rateResolver = rateResolver;
        this.splitter = splitter;
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(Long userId, String tripRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        List<Expense> expenses = expenseRepository.findByTripIdOrderBySpentAtDescIdDesc(trip.getId());
        if (expenses.isEmpty()) {
            return List.of();
        }
        Map<Long, String> memberRids = memberRidMap(trip.getId());
        Map<Long, String> placeRids = placeRidMap(trip.getId());
        ItineraryRids itinRids = itineraryRids(trip.getId());
        Map<Long, List<ExpenseShare>> sharesByExpense = shareRepository
                .findByExpenseIdIn(expenses.stream().map(Expense::getId).toList())
                .stream().collect(Collectors.groupingBy(ExpenseShare::getExpenseId));

        return expenses.stream()
                .map(e -> toResponse(e, memberRids, placeRids, itinRids,
                        sharesByExpense.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long userId, String tripRid, String expenseRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.VIEWER).trip();
        Expense e = loadInTrip(expenseRid, trip.getId());
        return toResponse(e, memberRidMap(trip.getId()), placeRidMap(trip.getId()),
                itineraryRids(trip.getId()), shareRepository.findByExpenseId(e.getId()));
    }

    @Transactional
    public ExpenseResponse create(Long userId, String tripRid, CreateExpenseRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();

        Long payerId = requireMemberId(request.payerRid(), trip.getId(), "Payer");
        Long placeId = placeService.resolvePlaceId(request.placeRid(), trip.getId());

        String currency = request.currency().toUpperCase();
        BigDecimal rate = rateResolver.resolve(currency, trip.getBaseCurrency(), request.exchangeRate());
        BigDecimal amount = moneyService.normalizeAmount(request.amount());
        BigDecimal amountBase = moneyService.toAmountBase(amount, rate);

        Expense expense = new Expense();
        expense.setTripId(trip.getId());
        expense.setTitle(request.title().trim());
        expense.setCategory(request.category());
        if (request.expenseType() != null) {
            expense.setExpenseType(request.expenseType());
        }
        expense.setCurrency(currency);
        expense.setAmount(amount);
        expense.setExchangeRate(rate);
        expense.setAmountBase(amountBase);
        expense.setPayerId(payerId);
        expense.setPlaceId(placeId);
        applyItinerary(expense, request.itineraryKind(), request.itineraryRid(), trip.getId());
        expense.setPaidFromFund(request.paidFromFund());
        expense.setNote(request.note());
        expense.setSpentAt(request.spentAt() != null ? request.spentAt() : Instant.now());
        expense = expenseRepository.save(expense);

        // A personal expense is split into shares; a fund-paid one creates no debt (SPEC §7 Module 11).
        if (!request.paidFromFund()) {
            persistShares(expense, trip.getId(), amountBase, request);
        }
        return get(userId, tripRid, expense.getRid());
    }

    @Transactional
    public ExpenseResponse update(Long userId, String tripRid, String expenseRid, UpdateExpenseRequest request) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Expense e = loadInTrip(expenseRid, trip.getId());
        if (request.title() != null) {
            e.setTitle(request.title().trim());
        }
        if (request.category() != null) {
            e.setCategory(request.category());
        }
        if (request.expenseType() != null) {
            e.setExpenseType(request.expenseType());
        }
        if (request.placeRid() != null) {
            e.setPlaceId(request.placeRid().isBlank()
                    ? null
                    : placeService.resolvePlaceId(request.placeRid(), trip.getId()));
        }
        // A non-null itineraryRid (re)sets the link; blank clears it. Omitted leaves it unchanged.
        if (request.itineraryRid() != null) {
            applyItinerary(e, request.itineraryKind(), request.itineraryRid(), trip.getId());
        }
        if (request.note() != null) {
            e.setNote(request.note().isBlank() ? null : request.note());
        }
        if (request.spentAt() != null) {
            e.setSpentAt(request.spentAt());
        }
        return toResponse(e, memberRidMap(trip.getId()), placeRidMap(trip.getId()),
                itineraryRids(trip.getId()), shareRepository.findByExpenseId(e.getId()));
    }

    @Transactional
    public void delete(Long userId, String tripRid, String expenseRid) {
        Trip trip = guard.requireByTripRid(tripRid, userId, MemberRole.EDITOR).trip();
        Expense e = loadInTrip(expenseRid, trip.getId());
        shareRepository.findByExpenseId(e.getId()).forEach(s -> s.setDeleted(true));
        e.setDeleted(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void persistShares(Expense expense, Long tripId, BigDecimal amountBase, CreateExpenseRequest request) {
        if (request.splitType() == null || request.participants() == null || request.participants().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "A personal expense needs a splitType and participants.");
        }
        List<ExpenseSplitter.Participant> parts = request.participants().stream()
                .map(p -> new ExpenseSplitter.Participant(
                        requireMemberId(p.memberRid(), tripId, "Split member"), valueOf(p)))
                .toList();
        Map<Long, BigDecimal> shares = splitter.split(amountBase, request.splitType(), parts);
        shares.forEach((memberId, shareBase) -> {
            ExpenseShare share = new ExpenseShare();
            share.setExpenseId(expense.getId());
            share.setMemberId(memberId);
            share.setShareBase(shareBase);
            shareRepository.save(share);
        });
    }

    private static BigDecimal valueOf(ExpenseShareInput input) {
        return input.value();
    }

    private Long requireMemberId(String memberRid, Long tripId, String label) {
        TripMember member = tripMemberRepository.findByRid(memberRid)
                .filter(m -> tripId.equals(m.getTripId()))
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        label + " is not a member of this trip."));
        return member.getId();
    }

    private Expense loadInTrip(String expenseRid, Long tripId) {
        Expense e = expenseRepository.findByRid(expenseRid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Expense not found."));
        if (!tripId.equals(e.getTripId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Expense not found.");
        }
        return e;
    }

    private Map<Long, String> memberRidMap(Long tripId) {
        return tripMemberRepository.findByTripId(tripId).stream()
                .collect(Collectors.toMap(TripMember::getId, TripMember::getRid));
    }

    private Map<Long, String> placeRidMap(Long tripId) {
        return placeRepository.findByTripIdOrderByNameAsc(tripId).stream()
                .collect(Collectors.toMap(Place::getId, Place::getRid));
    }

    private Map<Long, String> eventRidMap(Long tripId) {
        return eventRepository.findByTripIdOrderByStartTimeAsc(tripId).stream()
                .collect(Collectors.toMap(Event::getId, Event::getRid));
    }

    private Map<Long, String> transportRidMap(Long tripId) {
        return transportRepository.findByTripIdOrderByDepartureTimeAsc(tripId).stream()
                .collect(Collectors.toMap(Transport::getId, Transport::getRid));
    }

    private Map<Long, String> accommodationRidMap(Long tripId) {
        return accommodationRepository.findByTripIdOrderByCheckinTimeAsc(tripId).stream()
                .collect(Collectors.toMap(Accommodation::getId, Accommodation::getRid));
    }

    private ItineraryRids itineraryRids(Long tripId) {
        return new ItineraryRids(eventRidMap(tripId), transportRidMap(tripId), accommodationRidMap(tripId));
    }

    /** The rid maps for all three itinerary tables, so a polymorphic (kind, id) link resolves to a rid. */
    private record ItineraryRids(Map<Long, String> event, Map<Long, String> transport,
                                 Map<Long, String> accommodation) {
        String rid(ItineraryKind kind, Long id) {
            if (kind == null || id == null) {
                return null;
            }
            return switch (kind) {
                case EVENT -> event.get(id);
                case TRANSPORT -> transport.get(id);
                case ACCOMMODATION -> accommodation.get(id);
            };
        }
    }

    /**
     * Set (or clear) an expense's polymorphic itinerary link. A blank rid clears it; otherwise the
     * (kind, rid) target is validated to belong to this trip before its id is stored.
     */
    private void applyItinerary(Expense expense, String kindStr, String rid, Long tripId) {
        if (rid == null || rid.isBlank()) {
            expense.setItineraryKind(null);
            expense.setItineraryId(null);
            return;
        }
        if (kindStr == null || kindStr.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "itineraryKind is required when itineraryRid is given.");
        }
        ItineraryKind kind;
        try {
            kind = ItineraryKind.valueOf(kindStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown itineraryKind: " + kindStr);
        }
        Long id = switch (kind) {
            case EVENT -> eventRepository.findByRid(rid)
                    .filter(x -> tripId.equals(x.getTripId())).map(Event::getId).orElse(null);
            case TRANSPORT -> transportRepository.findByRid(rid)
                    .filter(x -> tripId.equals(x.getTripId())).map(Transport::getId).orElse(null);
            case ACCOMMODATION -> accommodationRepository.findByRid(rid)
                    .filter(x -> tripId.equals(x.getTripId())).map(Accommodation::getId).orElse(null);
        };
        if (id == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Itinerary item is not part of this trip.");
        }
        expense.setItineraryKind(kind);
        expense.setItineraryId(id);
    }

    private ExpenseResponse toResponse(Expense e, Map<Long, String> memberRids,
                                       Map<Long, String> placeRids, ItineraryRids itinRids,
                                       List<ExpenseShare> shares) {
        List<ShareView> shareViews = shares.stream()
                .map(s -> new ShareView(memberRids.get(s.getMemberId()), s.getShareBase()))
                .filter(v -> Objects.nonNull(v.memberRid()))
                .toList();
        return ExpenseResponse.from(e,
                memberRids.get(e.getPayerId()),
                e.getPlaceId() == null ? null : placeRids.get(e.getPlaceId()),
                itinRids.rid(e.getItineraryKind(), e.getItineraryId()),
                shareViews);
    }
}
