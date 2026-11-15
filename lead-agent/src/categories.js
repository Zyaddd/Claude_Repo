// Business types worth pitching AI automation to (booking, reminders, chat, follow-ups).
// `osm` lists OpenStreetMap tag filters; `query` is the Google search phrase.
export const CATEGORIES = [
  { id: 'dentist', label: 'Dentists', query: 'dentist', osm: ['amenity=dentist', 'healthcare=dentist'] },
  { id: 'clinic', label: 'Clinics & doctors', query: 'medical clinic', osm: ['amenity=clinic', 'amenity=doctors'] },
  { id: 'hair', label: 'Hair & barber shops', query: 'hair salon', osm: ['shop=hairdresser'] },
  { id: 'beauty', label: 'Beauty & spa', query: 'beauty salon spa', osm: ['shop=beauty', 'leisure=spa', 'shop=massage'] },
  { id: 'gym', label: 'Gyms & fitness', query: 'gym fitness studio', osm: ['leisure=fitness_centre', 'leisure=sports_centre'] },
  { id: 'restaurant', label: 'Restaurants', query: 'restaurant', osm: ['amenity=restaurant'] },
  { id: 'cafe', label: 'Cafés', query: 'cafe', osm: ['amenity=cafe'] },
  { id: 'realestate', label: 'Real estate agents', query: 'real estate agency', osm: ['office=estate_agent', 'shop=estate_agent'] },
  { id: 'carrepair', label: 'Car repair & service', query: 'auto repair shop', osm: ['shop=car_repair', 'shop=tyres'] },
  { id: 'cardealer', label: 'Car dealers', query: 'car dealer', osm: ['shop=car'] },
  { id: 'lawyer', label: 'Law firms', query: 'law firm', osm: ['office=lawyer'] },
  { id: 'accountant', label: 'Accountants', query: 'accounting firm', osm: ['office=accountant', 'office=tax_advisor'] },
  { id: 'hotel', label: 'Hotels', query: 'hotel', osm: ['tourism=hotel', 'tourism=guest_house'] },
  { id: 'vet', label: 'Veterinarians', query: 'veterinary clinic', osm: ['amenity=veterinary'] },
  { id: 'school', label: 'Tutoring & training', query: 'training center tutoring', osm: ['amenity=language_school', 'amenity=driving_school', 'office=educational_institution'] },
  { id: 'trades', label: 'Plumbers & electricians', query: 'plumber electrician', osm: ['craft=plumber', 'craft=electrician', 'craft=hvac'] },
  { id: 'cleaning', label: 'Cleaning services', query: 'cleaning service', osm: ['shop=dry_cleaning', 'shop=laundry'] },
  { id: 'pharmacy', label: 'Pharmacies', query: 'pharmacy', osm: ['amenity=pharmacy'] },
];

export const categoryById = (id) => CATEGORIES.find((c) => c.id === id);
