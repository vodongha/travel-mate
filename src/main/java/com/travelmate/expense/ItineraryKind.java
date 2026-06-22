package com.travelmate.expense;

/**
 * What kind of itinerary item an expense is attached to. The trip's timeline is composed of three
 * independent tables — generic events, transport legs, and accommodation stays — so an expense's
 * link is polymorphic: {@code (itineraryKind, itineraryId)} together identify the target row.
 * Null kind means the expense is standalone (not attached to anything on the itinerary).
 */
public enum ItineraryKind {
    EVENT,
    TRANSPORT,
    ACCOMMODATION
}
