from app.demo_analysis import analyze_location


def test_priced_week_has_all_five_categories_across_budapest_districts():
    report = analyze_location("budapest-priced-week", "Budapest V")
    assert report.dataSource == "SYNTHETIC"
    assert report.visitCount == 5
    assert report.distinctDistricts == 5
    assert {visit.place for visit in report.visits} == {"Grocery store", "Library", "GYM", "Café", "Starbucks"}
    assert report == analyze_location("budapest-priced-week", "Budapest V")


def test_existing_scenarios_keep_their_visits_and_unsupported_places():
    regular = analyze_location("regular-week", None)
    assert regular.visitCount == 4
    assert {visit.place for visit in regular.visits} == {"Library", "Grocery shop", "Park", "Market"}
    assert analyze_location("sparse", None).visitCount == 1
    assert analyze_location("contradictory", None).visitCount == 2
