package com.example.data

data class DestinationPreset(
    val name: String,
    val category: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val iconName: String = "place"
)

object PresetDestinations {
    val list = listOf(
        DestinationPreset(
            name = "Taj Mahal, Agra",
            category = "Historic / Heritage",
            description = "Agra, Uttar Pradesh, India",
            latitude = 27.1751,
            longitude = 78.0421
        ),
        DestinationPreset(
            name = "India Gate, New Delhi",
            category = "Capital / Monument",
            description = "Rajpath, New Delhi, India",
            latitude = 28.6129,
            longitude = 77.2295
        ),
        DestinationPreset(
            name = "Gateway of India, Mumbai",
            category = "Coastal / City",
            description = "Colaba, Mumbai, Maharashtra",
            latitude = 18.9220,
            longitude = 72.8347
        ),
        DestinationPreset(
            name = "Baga Beach, Goa",
            category = "Coastal / Beach",
            description = "North Goa, India",
            latitude = 15.5553,
            longitude = 73.7517
        ),
        DestinationPreset(
            name = "Bengaluru City Center",
            category = "Tech City",
            description = "MG Road, Bengaluru, Karnataka",
            latitude = 12.9716,
            longitude = 77.5946
        ),
        DestinationPreset(
            name = "Howrah Bridge, Kolkata",
            category = "River / Historic",
            description = "Hooghly River, Kolkata, West Bengal",
            latitude = 22.5851,
            longitude = 88.3468
        ),
        DestinationPreset(
            name = "Mount Everest Peak",
            category = "Mountain / Nature",
            description = "Himalayas, Nepal / Tibet border",
            latitude = 27.9881,
            longitude = 86.9250
        ),
        DestinationPreset(
            name = "Burj Khalifa, Dubai",
            category = "International / Skyscraper",
            description = "Downtown Dubai, UAE",
            latitude = 25.1972,
            longitude = 55.2744
        ),
        DestinationPreset(
            name = "Eiffel Tower, Paris",
            category = "International / Landmark",
            description = "Champ de Mars, Paris, France",
            latitude = 48.8584,
            longitude = 2.2945
        ),
        DestinationPreset(
            name = "Times Square, New York",
            category = "International / Metropolis",
            description = "Manhattan, New York, USA",
            latitude = 40.7580,
            longitude = -73.9855
        ),
        DestinationPreset(
            name = "Tokyo Tower, Japan",
            category = "International / Landmark",
            description = "Minato City, Tokyo, Japan",
            latitude = 35.6586,
            longitude = 139.7454
        )
    )
}
