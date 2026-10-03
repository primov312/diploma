-- Academic demo catalog. Keep existing partner/product IDs and internal slugs stable.
-- All entries and prices are illustrative; stock photos are not official listings.
ALTER TABLE products ADD COLUMN description VARCHAR(500);
ALTER TABLE products ADD COLUMN category VARCHAR(50);
ALTER TABLE products ADD COLUMN image_path VARCHAR(180);
ALTER TABLE products ADD COLUMN image_alt VARCHAR(250);

UPDATE partners SET display_name = 'Netflix' WHERE slug = 'streambox';
UPDATE partners SET display_name = 'Amazon' WHERE slug = 'markethub';
UPDATE partners SET display_name = 'Zara' WHERE slug = 'threadly';

UPDATE transactions SET description = 'Demo purchase at Netflix' WHERE fixture_id LIKE '%-streambox-%' AND description = 'Demo purchase at StreamBox';
UPDATE transactions SET description = 'Demo purchase at Amazon' WHERE fixture_id LIKE '%-markethub-%' AND description = 'Demo purchase at MarketHub';
UPDATE transactions SET description = 'Demo purchase at Zara' WHERE fixture_id LIKE '%-threadly-%' AND description = 'Demo purchase at Threadly';

INSERT INTO products (partner_id, fixture_id, name, price, description, category, image_path, image_alt)
SELECT p.id, c.fixture_id, c.name, c.price, c.description, c.category, c.image_path, c.image_alt
FROM (VALUES
  ('streambox', 'streambox-basic-monthly', 'Basic monthly plan', 9.99, 'A one-month streaming plan for a single screen in the demo catalog.', 'Plans', '/catalog/streambox-basic-monthly.jpg', 'Illustrative photo: television remote control'),
  ('streambox', 'streambox-standard-monthly', 'Standard monthly plan', 15.99, 'A one-month plan for two screens with an expanded viewing selection.', 'Plans', '/catalog/streambox-standard-monthly.jpg', 'Illustrative photo: watching television living room'),
  ('streambox', 'streambox-4k-monthly', '4K + HDR monthly plan', 24.99, 'An illustrative high-resolution monthly viewing plan.', 'Plans', '/catalog/streambox-4k-monthly.jpg', 'Illustrative photo: 4k television screen'),
  ('streambox', 'streambox-premium-annual', 'Premium annual plan', 159.99, 'Twelve months of premium access shown as one demo purchase.', 'Plans', '/catalog/streambox-premium-annual.jpg', 'Illustrative photo: home cinema projector'),
  ('streambox', 'streambox-family-annual', 'Family annual plan', 199.99, 'An annual multi-screen plan for a household in this demo.', 'Plans', '/catalog/streambox-family-annual.jpg', 'Illustrative photo: family watching television'),
  ('streambox', 'streambox-northern-lights', 'Northern Lights', 7.99, 'A fictional travel film following a photographer across winter skies.', 'Films', '/catalog/streambox-northern-lights.jpg', 'Illustrative photo: aurora borealis night sky'),
  ('streambox', 'streambox-last-train', 'The Last Train Home', 6.99, 'A fictional quiet drama about an unexpected trip back to a hometown.', 'Films', '/catalog/streambox-last-train.jpg', 'Illustrative photo: train station evening'),
  ('streambox', 'streambox-deep-blue', 'Deep Blue Current', 8.99, 'A fictional ocean adventure about a team mapping a hidden reef.', 'Films', '/catalog/streambox-deep-blue.jpg', 'Illustrative photo: underwater coral reef'),
  ('streambox', 'streambox-city-after-rain', 'City After Rain', 6.99, 'A fictional urban mystery set across one stormy evening.', 'Films', '/catalog/streambox-city-after-rain.jpg', 'Illustrative photo: city street rain night'),
  ('streambox', 'streambox-red-desert', 'Red Desert Road', 7.99, 'A fictional road story through wide desert landscapes.', 'Films', '/catalog/streambox-red-desert.jpg', 'Illustrative photo: desert road landscape'),
  ('streambox', 'streambox-glass-garden', 'The Glass Garden', 6.99, 'A fictional family story centered on a remarkable greenhouse.', 'Films', '/catalog/streambox-glass-garden.jpg', 'Illustrative photo: greenhouse plants glass'),
  ('streambox', 'streambox-orbit', 'Orbit 17', 8.99, 'A fictional science-fiction rescue mission above Earth.', 'Films', '/catalog/streambox-orbit.jpg', 'Illustrative photo: earth from space astronaut'),
  ('streambox', 'streambox-harbor', 'Harbor at Dawn', 6.99, 'A fictional coastal drama about a returning sailor.', 'Films', '/catalog/streambox-harbor.jpg', 'Illustrative photo: harbor sunrise boats'),
  ('streambox', 'streambox-forest', 'Forest Signal', 7.99, 'A fictional adventure following a radio signal into the woods.', 'Films', '/catalog/streambox-forest.jpg', 'Illustrative photo: forest trail sunlight'),
  ('streambox', 'streambox-mountain', 'Beyond the Summit', 8.99, 'A fictional climbing documentary about teamwork in the mountains.', 'Films', '/catalog/streambox-mountain.jpg', 'Illustrative photo: mountain climbing hikers'),
  ('streambox', 'streambox-night-shift', 'Night Shift', 12.99, 'A fictional workplace series following an overnight hospital team.', 'Series', '/catalog/streambox-night-shift.jpg', 'Illustrative photo: hospital hallway night'),
  ('streambox', 'streambox-corner-cafe', 'The Corner Café', 11.99, 'A fictional character series centered on a neighborhood café.', 'Series', '/catalog/streambox-corner-cafe.jpg', 'Illustrative photo: cafe interior coffee'),
  ('streambox', 'streambox-hidden-islands', 'Hidden Islands', 13.99, 'A fictional nature series visiting remote island habitats.', 'Series', '/catalog/streambox-hidden-islands.jpg', 'Illustrative photo: tropical island aerial'),
  ('streambox', 'streambox-makers', 'Makers & Machines', 12.99, 'A fictional documentary series about modern workshops.', 'Series', '/catalog/streambox-makers.jpg', 'Illustrative photo: workshop tools maker'),
  ('streambox', 'streambox-capital-files', 'Capital Files', 13.99, 'A fictional investigative series set in a busy capital city.', 'Series', '/catalog/streambox-capital-files.jpg', 'Illustrative photo: city skyline dusk'),
  ('streambox', 'streambox-river-house', 'River House', 11.99, 'A fictional family series set beside a winding river.', 'Series', '/catalog/streambox-river-house.jpg', 'Illustrative photo: river house landscape'),
  ('streambox', 'streambox-after-school', 'After School Club', 10.99, 'A fictional coming-of-age series about a student project team.', 'Series', '/catalog/streambox-after-school.jpg', 'Illustrative photo: students school classroom'),
  ('streambox', 'streambox-open-kitchen', 'Open Kitchen', 11.99, 'A fictional food series exploring home cooks and recipes.', 'Series', '/catalog/streambox-open-kitchen.jpg', 'Illustrative photo: cooking kitchen food'),
  ('streambox', 'streambox-timekeepers', 'The Timekeepers', 13.99, 'A fictional mystery series built around an old clock workshop.', 'Series', '/catalog/streambox-timekeepers.jpg', 'Illustrative photo: vintage clock workshop'),
  ('streambox', 'streambox-wild-coast', 'Wild Coast', 12.99, 'A fictional nature series about coastal wildlife.', 'Series', '/catalog/streambox-wild-coast.jpg', 'Illustrative photo: sea coast wildlife'),
  ('streambox', 'streambox-documentary-bundle', 'Documentary bundle', 39.99, 'An illustrative bundle of science, history, and nature viewing.', 'Bundles', '/catalog/streambox-documentary-bundle.jpg', 'Illustrative photo: documentary camera nature'),
  ('streambox', 'streambox-film-night-bundle', 'Film night bundle', 29.99, 'An illustrative collection of four film-night selections.', 'Bundles', '/catalog/streambox-film-night-bundle.jpg', 'Illustrative photo: movie theater seats'),
  ('streambox', 'streambox-family-weekend', 'Family weekend bundle', 24.99, 'A fictional weekend viewing collection for a family.', 'Bundles', '/catalog/streambox-family-weekend.jpg', 'Illustrative photo: family movie night'),
  ('streambox', 'streambox-series-season', 'Series season bundle', 49.99, 'An illustrative full-season access package for one series.', 'Bundles', '/catalog/streambox-series-season.jpg', 'Illustrative photo: television series screen'),
  ('streambox', 'streambox-cinema-year', 'Cinema year bundle', 129.99, 'An illustrative annual collection of rotating films.', 'Bundles', '/catalog/streambox-cinema-year.jpg', 'Illustrative photo: cinema film projector'),
  ('markethub', 'markethub-earbuds', 'Wireless earbuds', 129.00, 'Compact in-ear audio with a portable charging case.', 'Audio', '/catalog/markethub-earbuds.jpg', 'Illustrative photo: wireless earbuds case'),
  ('markethub', 'markethub-monitor-27', '27-inch QHD monitor', 279.00, 'A large QHD display for a home desk setup.', 'Workspace', '/catalog/markethub-monitor-27.jpg', 'Illustrative photo: computer monitor desk'),
  ('markethub', 'markethub-robot-vacuum', 'Robot vacuum', 349.00, 'An automated floor cleaner for everyday household use.', 'Home', '/catalog/markethub-robot-vacuum.jpg', 'Illustrative photo: robot vacuum cleaner'),
  ('markethub', 'markethub-espresso', 'Espresso machine', 499.00, 'A countertop machine for espresso and milk drinks.', 'Kitchen', '/catalog/markethub-espresso.jpg', 'Illustrative photo: espresso machine kitchen'),
  ('markethub', 'markethub-standing-desk', 'Standing desk', 599.00, 'An adjustable desk that supports seated and standing work.', 'Workspace', '/catalog/markethub-standing-desk.jpg', 'Illustrative photo: standing desk office'),
  ('markethub', 'markethub-bluetooth-speaker', 'Bluetooth speaker', 89.00, 'A portable speaker for music around the home.', 'Audio', '/catalog/markethub-bluetooth-speaker.jpg', 'Illustrative photo: portable bluetooth speaker'),
  ('markethub', 'markethub-headphones', 'Over-ear headphones', 179.00, 'Comfortable headphones for focused listening.', 'Audio', '/catalog/markethub-headphones.jpg', 'Illustrative photo: over ear headphones'),
  ('markethub', 'markethub-soundbar', 'Compact soundbar', 249.00, 'A slim speaker unit for improving television sound.', 'Audio', '/catalog/markethub-soundbar.jpg', 'Illustrative photo: television soundbar'),
  ('markethub', 'markethub-turntable', 'Record turntable', 219.00, 'A simple turntable for playing vinyl records.', 'Audio', '/catalog/markethub-turntable.jpg', 'Illustrative photo: vinyl record turntable'),
  ('markethub', 'markethub-keyboard', 'Mechanical keyboard', 119.00, 'A tactile keyboard for a productive desktop setup.', 'Workspace', '/catalog/markethub-keyboard.jpg', 'Illustrative photo: mechanical keyboard'),
  ('markethub', 'markethub-mouse', 'Wireless mouse', 49.00, 'A cordless pointing device for laptop and desktop work.', 'Workspace', '/catalog/markethub-mouse.jpg', 'Illustrative photo: wireless computer mouse'),
  ('markethub', 'markethub-webcam', 'HD webcam', 79.00, 'A desktop camera for clear video calls.', 'Workspace', '/catalog/markethub-webcam.jpg', 'Illustrative photo: webcam computer'),
  ('markethub', 'markethub-desk-lamp', 'LED desk lamp', 69.00, 'An adjustable task light for reading and working.', 'Workspace', '/catalog/markethub-desk-lamp.jpg', 'Illustrative photo: led desk lamp'),
  ('markethub', 'markethub-office-chair', 'Ergonomic office chair', 429.00, 'A supportive chair for a home office.', 'Workspace', '/catalog/markethub-office-chair.jpg', 'Illustrative photo: ergonomic office chair'),
  ('markethub', 'markethub-tablet', '10-inch tablet', 399.00, 'A portable screen for reading, browsing, and media.', 'Tech', '/catalog/markethub-tablet.jpg', 'Illustrative photo: tablet computer device'),
  ('markethub', 'markethub-ereader', 'E-reader', 139.00, 'A lightweight digital reader with a paper-like screen.', 'Tech', '/catalog/markethub-ereader.jpg', 'Illustrative photo: e reader device'),
  ('markethub', 'markethub-camera', 'Mirrorless camera', 899.00, 'A compact interchangeable-lens camera for photography.', 'Tech', '/catalog/markethub-camera.jpg', 'Illustrative photo: mirrorless digital camera'),
  ('markethub', 'markethub-tripod', 'Travel tripod', 89.00, 'A folding support for stable camera shots.', 'Tech', '/catalog/markethub-tripod.jpg', 'Illustrative photo: camera tripod'),
  ('markethub', 'markethub-projector', 'Home projector', 749.00, 'A portable projector for large-screen viewing.', 'Tech', '/catalog/markethub-projector.jpg', 'Illustrative photo: home projector'),
  ('markethub', 'markethub-air-purifier', 'Air purifier', 299.00, 'A compact unit for filtering indoor air.', 'Home', '/catalog/markethub-air-purifier.jpg', 'Illustrative photo: home air purifier'),
  ('markethub', 'markethub-coffee-grinder', 'Coffee grinder', 119.00, 'An electric grinder for fresh coffee beans.', 'Kitchen', '/catalog/markethub-coffee-grinder.jpg', 'Illustrative photo: coffee grinder'),
  ('markethub', 'markethub-air-fryer', 'Air fryer', 159.00, 'A countertop cooker for crisp meals with circulating air.', 'Kitchen', '/catalog/markethub-air-fryer.jpg', 'Illustrative photo: air fryer kitchen'),
  ('markethub', 'markethub-blender', 'Countertop blender', 99.00, 'A blender for smoothies, sauces, and soups.', 'Kitchen', '/catalog/markethub-blender.jpg', 'Illustrative photo: kitchen blender'),
  ('markethub', 'markethub-kettle', 'Electric kettle', 59.00, 'A quick-boil kettle for tea and hot drinks.', 'Kitchen', '/catalog/markethub-kettle.jpg', 'Illustrative photo: electric kettle'),
  ('markethub', 'markethub-floor-lamp', 'Floor lamp', 129.00, 'A freestanding light for a living room corner.', 'Home', '/catalog/markethub-floor-lamp.jpg', 'Illustrative photo: floor lamp living room'),
  ('markethub', 'markethub-bookshelf', 'Modular bookshelf', 249.00, 'A flexible shelf for books and home storage.', 'Home', '/catalog/markethub-bookshelf.jpg', 'Illustrative photo: bookshelf furniture'),
  ('markethub', 'markethub-plant-stand', 'Plant stand', 69.00, 'A raised display for indoor plants.', 'Home', '/catalog/markethub-plant-stand.jpg', 'Illustrative photo: indoor plant stand'),
  ('markethub', 'markethub-luggage', 'Carry-on suitcase', 189.00, 'A compact wheeled case for short trips.', 'Travel', '/catalog/markethub-luggage.jpg', 'Illustrative photo: carry on suitcase'),
  ('markethub', 'markethub-backpack', 'Travel backpack', 119.00, 'A durable daypack with room for daily essentials.', 'Travel', '/catalog/markethub-backpack.jpg', 'Illustrative photo: travel backpack'),
  ('markethub', 'markethub-bike-helmet', 'Cycling helmet', 79.00, 'A lightweight protective helmet for cycling.', 'Travel', '/catalog/markethub-bike-helmet.jpg', 'Illustrative photo: bicycle helmet'),
  ('threadly', 'threadly-linen-shirt', 'Linen shirt', 59.00, 'A breathable linen shirt for warm days.', 'Tops', '/catalog/threadly-linen-shirt.jpg', 'Illustrative photo: linen shirt clothing'),
  ('threadly', 'threadly-denim-jacket', 'Denim jacket', 99.00, 'A versatile denim layer for changing seasons.', 'Outerwear', '/catalog/threadly-denim-jacket.jpg', 'Illustrative photo: denim jacket fashion'),
  ('threadly', 'threadly-running-shoes', 'Running shoes', 129.00, 'Lightweight trainers for everyday movement.', 'Shoes', '/catalog/threadly-running-shoes.jpg', 'Illustrative photo: running shoes sneakers'),
  ('threadly', 'threadly-wool-coat', 'Wool coat', 189.00, 'A structured coat for colder weather.', 'Outerwear', '/catalog/threadly-wool-coat.jpg', 'Illustrative photo: wool coat fashion'),
  ('threadly', 'threadly-leather-boots', 'Leather boots', 219.00, 'Sturdy ankle boots for daily wear.', 'Shoes', '/catalog/threadly-leather-boots.jpg', 'Illustrative photo: leather boots footwear'),
  ('threadly', 'threadly-cotton-tee', 'Cotton T-shirt', 25.00, 'A soft everyday tee with a relaxed fit.', 'Tops', '/catalog/threadly-cotton-tee.jpg', 'Illustrative photo: cotton t shirt clothing'),
  ('threadly', 'threadly-striped-shirt', 'Striped poplin shirt', 55.00, 'A crisp striped shirt for casual or office wear.', 'Tops', '/catalog/threadly-striped-shirt.jpg', 'Illustrative photo: striped shirt fashion'),
  ('threadly', 'threadly-knit-cardigan', 'Knit cardigan', 79.00, 'A warm button-front layer for cooler days.', 'Knitwear', '/catalog/threadly-knit-cardigan.jpg', 'Illustrative photo: knit cardigan sweater'),
  ('threadly', 'threadly-cashmere-sweater', 'Cashmere-blend sweater', 149.00, 'A soft knit designed for easy layering.', 'Knitwear', '/catalog/threadly-cashmere-sweater.jpg', 'Illustrative photo: cashmere sweater'),
  ('threadly', 'threadly-turtleneck', 'Ribbed turtleneck', 49.00, 'A close-fitting knit for layering under jackets.', 'Knitwear', '/catalog/threadly-turtleneck.jpg', 'Illustrative photo: turtleneck sweater'),
  ('threadly', 'threadly-jeans', 'Straight-leg jeans', 69.00, 'Classic denim jeans with a straight silhouette.', 'Bottoms', '/catalog/threadly-jeans.jpg', 'Illustrative photo: blue jeans clothing'),
  ('threadly', 'threadly-trousers', 'Tailored trousers', 89.00, 'Clean-cut trousers for smart everyday outfits.', 'Bottoms', '/catalog/threadly-trousers.jpg', 'Illustrative photo: tailored trousers fashion'),
  ('threadly', 'threadly-cargo-pants', 'Cargo pants', 75.00, 'Utility-inspired trousers with practical pockets.', 'Bottoms', '/catalog/threadly-cargo-pants.jpg', 'Illustrative photo: cargo pants clothing'),
  ('threadly', 'threadly-midi-skirt', 'Pleated midi skirt', 69.00, 'A flowing skirt with soft pleats.', 'Bottoms', '/catalog/threadly-midi-skirt.jpg', 'Illustrative photo: pleated midi skirt'),
  ('threadly', 'threadly-summer-dress', 'Cotton summer dress', 89.00, 'An easy cotton dress for warm weather.', 'Dresses', '/catalog/threadly-summer-dress.jpg', 'Illustrative photo: summer dress fashion'),
  ('threadly', 'threadly-evening-dress', 'Evening dress', 169.00, 'A refined long dress for evening occasions.', 'Dresses', '/catalog/threadly-evening-dress.jpg', 'Illustrative photo: evening dress fashion'),
  ('threadly', 'threadly-shirt-dress', 'Shirt dress', 99.00, 'A collared dress with a relaxed fit.', 'Dresses', '/catalog/threadly-shirt-dress.jpg', 'Illustrative photo: shirt dress fashion'),
  ('threadly', 'threadly-blazer', 'Single-breasted blazer', 149.00, 'A tailored layer for work and weekend outfits.', 'Outerwear', '/catalog/threadly-blazer.jpg', 'Illustrative photo: single breasted blazer'),
  ('threadly', 'threadly-trench', 'Lightweight trench coat', 179.00, 'A practical long coat for mild rainy days.', 'Outerwear', '/catalog/threadly-trench.jpg', 'Illustrative photo: trench coat clothing'),
  ('threadly', 'threadly-puffer', 'Quilted puffer jacket', 159.00, 'A padded jacket for cold-weather layering.', 'Outerwear', '/catalog/threadly-puffer.jpg', 'Illustrative photo: puffer jacket clothing'),
  ('threadly', 'threadly-sneakers', 'Canvas sneakers', 69.00, 'Simple low-top shoes for casual outfits.', 'Shoes', '/catalog/threadly-sneakers.jpg', 'Illustrative photo: canvas sneakers shoes'),
  ('threadly', 'threadly-loafers', 'Classic loafers', 99.00, 'Slip-on shoes with a polished everyday look.', 'Shoes', '/catalog/threadly-loafers.jpg', 'Illustrative photo: leather loafers shoes'),
  ('threadly', 'threadly-sandals', 'Leather sandals', 79.00, 'Open-toe sandals for sunny days.', 'Shoes', '/catalog/threadly-sandals.jpg', 'Illustrative photo: leather sandals fashion'),
  ('threadly', 'threadly-tote', 'Canvas tote bag', 39.00, 'A roomy reusable bag for daily essentials.', 'Accessories', '/catalog/threadly-tote.jpg', 'Illustrative photo: canvas tote bag'),
  ('threadly', 'threadly-crossbody', 'Crossbody bag', 89.00, 'A compact hands-free bag with an adjustable strap.', 'Accessories', '/catalog/threadly-crossbody.jpg', 'Illustrative photo: crossbody bag fashion'),
  ('threadly', 'threadly-scarf', 'Woven scarf', 35.00, 'A light woven scarf for layering.', 'Accessories', '/catalog/threadly-scarf.jpg', 'Illustrative photo: woven scarf fashion'),
  ('threadly', 'threadly-belt', 'Leather belt', 45.00, 'A simple belt to finish casual and formal looks.', 'Accessories', '/catalog/threadly-belt.jpg', 'Illustrative photo: leather belt clothing'),
  ('threadly', 'threadly-sunglasses', 'Square sunglasses', 59.00, 'Angular sunglasses for bright days.', 'Accessories', '/catalog/threadly-sunglasses.jpg', 'Illustrative photo: square sunglasses fashion'),
  ('threadly', 'threadly-beanie', 'Ribbed beanie', 29.00, 'A soft knit hat for cold weather.', 'Accessories', '/catalog/threadly-beanie.jpg', 'Illustrative photo: knit beanie hat'),
  ('threadly', 'threadly-weekender', 'Weekender bag', 119.00, 'A soft travel bag for short trips.', 'Accessories', '/catalog/threadly-weekender.jpg', 'Illustrative photo: weekender travel bag')
) AS c(partner_slug, fixture_id, name, price, description, category, image_path, image_alt)
JOIN partners p ON p.slug = c.partner_slug
ON CONFLICT (fixture_id) DO UPDATE SET
  name = EXCLUDED.name, price = EXCLUDED.price, partner_id = EXCLUDED.partner_id,
  description = EXCLUDED.description, category = EXCLUDED.category,
  image_path = EXCLUDED.image_path, image_alt = EXCLUDED.image_alt;

ALTER TABLE products ALTER COLUMN description SET NOT NULL;
ALTER TABLE products ALTER COLUMN category SET NOT NULL;
ALTER TABLE products ALTER COLUMN image_path SET NOT NULL;
ALTER TABLE products ALTER COLUMN image_alt SET NOT NULL;
