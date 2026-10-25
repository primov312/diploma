"""Location activity: synthetic visit records summarised by district. No model is involved."""
from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class LocationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    scenarioId: str = Field(..., min_length=1, max_length=60)
    declaredDistrict: str | None = Field(None, max_length=100)


class LocationVisit(BaseModel):
    id: str
    district: str
    place: str
    arrival: datetime
    departure: datetime


class LocationReport(BaseModel):
    scenarioId: str
    status: Literal["COMPLETE"]
    dataSource: Literal["SYNTHETIC"] = "SYNTHETIC"
    observedFrom: datetime | None
    observedTo: datetime | None
    visitCount: int
    distinctDistricts: int
    mostVisitedDistrict: str | None
    declaredDistrictComparison: str
    visits: list[LocationVisit]
    limitation: str


LOCATION_SCENARIOS: dict[str, list[dict]] = {
    "budapest-priced-month": [
        {"id": "month-grocery", "district": "Budapest II", "place": "Grocery store", "arrival": "2026-09-01T17:00:00Z", "departure": "2026-09-01T17:30:00Z"},
        {"id": "month-library", "district": "Budapest V", "place": "Library", "arrival": "2026-09-09T09:00:00Z", "departure": "2026-09-09T11:00:00Z"},
        *[{"id": f"month-gym-{day}", "district": "Budapest VIII", "place": "GYM", "arrival": f"2026-09-{day:02d}T18:00:00Z", "departure": f"2026-09-{day:02d}T19:00:00Z"} for day in (3,12,24)],
        *[{"id": f"month-cafe-{day}", "district": "Budapest XIII", "place": "Café", "arrival": f"2026-09-{day:02d}T12:00:00Z", "departure": f"2026-09-{day:02d}T12:20:00Z"} for day in (5,10,15,20,25,30)],
        *[{"id": f"month-starbucks-{day}", "district": "Budapest VI", "place": "Starbucks", "arrival": f"2026-09-{day:02d}T15:00:00Z", "departure": f"2026-09-{day:02d}T15:30:00Z"} for day in (8,18)],
    ],
    "budapest-priced-week": [
        {"id": "priced-01", "district": "Budapest II", "place": "Grocery store", "arrival": "2026-10-01T17:00:00Z", "departure": "2026-10-01T17:30:00Z"},
        {"id": "priced-02", "district": "Budapest V", "place": "Library", "arrival": "2026-10-02T09:00:00Z", "departure": "2026-10-02T11:00:00Z"},
        {"id": "priced-03", "district": "Budapest VIII", "place": "GYM", "arrival": "2026-10-02T18:00:00Z", "departure": "2026-10-02T19:00:00Z"},
        {"id": "priced-04", "district": "Budapest XIII", "place": "Café", "arrival": "2026-10-03T12:00:00Z", "departure": "2026-10-03T12:20:00Z"},
        {"id": "priced-05", "district": "Budapest VI", "place": "Starbucks", "arrival": "2026-10-03T15:00:00Z", "departure": "2026-10-03T15:30:00Z"},
    ],
    "regular-week": [
        {"id": "visit-01", "district": "Budapest V", "place": "Library", "arrival": "2026-09-01T09:00:00Z", "departure": "2026-09-01T11:00:00Z"},
        {"id": "visit-02", "district": "Budapest V", "place": "Grocery shop", "arrival": "2026-09-03T17:00:00Z", "departure": "2026-09-03T17:30:00Z"},
        {"id": "visit-03", "district": "Budapest XI", "place": "Park", "arrival": "2026-09-05T14:00:00Z", "departure": "2026-09-05T15:30:00Z"},
        {"id": "visit-04", "district": "Budapest V", "place": "Market", "arrival": "2026-09-07T10:00:00Z", "departure": "2026-09-07T10:45:00Z"},
    ],
    "sparse": [{"id": "visit-10", "district": "Budapest XI", "place": "Cafe", "arrival": "2026-09-06T12:00:00Z", "departure": "2026-09-06T12:20:00Z"}],
    "contradictory": [
        {"id": "visit-20", "district": "Budapest XIII", "place": "Station", "arrival": "2026-09-02T08:00:00Z", "departure": "2026-09-02T08:15:00Z"},
        {"id": "visit-21", "district": "Budapest XI", "place": "Office", "arrival": "2026-09-03T08:00:00Z", "departure": "2026-09-03T16:00:00Z"},
    ],
}

def analyze_location(scenario_id: str, declared_district: str | None = None) -> LocationReport:
    if scenario_id not in LOCATION_SCENARIOS:
        raise ValueError("unknown location scenario")
    visits = [LocationVisit.model_validate(visit) for visit in LOCATION_SCENARIOS[scenario_id]]
    counts: dict[str, int] = {}
    for visit in visits:
        counts[visit.district] = counts.get(visit.district, 0) + 1
    frequent = max(counts, key=counts.get) if counts else None
    if len(visits) < 3:
        comparison = "INSUFFICIENT_HISTORY"
    elif declared_district is None:
        comparison = "NO_DECLARED_DISTRICT"
    elif frequent == declared_district:
        comparison = "MATCHES_DECLARED_DISTRICT"
    else:
        comparison = "DOES_NOT_MATCH_DECLARED_DISTRICT"
    times = [visit.arrival for visit in visits]
    return LocationReport(
        scenarioId=scenario_id, status="COMPLETE", observedFrom=min(times) if times else None,
        observedTo=max(times) if times else None, visitCount=len(visits), distinctDistricts=len(counts),
        mostVisitedDistrict=frequent, declaredDistrictComparison=comparison, visits=visits,
        limitation="Synthetic visit records show scenario coverage only; they do not prove residence, work, or reliability.",
    )

