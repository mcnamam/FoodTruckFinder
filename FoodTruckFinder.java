import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class FoodTruckFinder {

    private static final String SF_GOV_URL_PREFIX = "http://data.sfgov.org/resource/bbb8-hzi6.json";

    public static void main(String[] args) {
        try {

            // determine the current day of the week, hour, and min
            final Calendar calendar = Calendar.getInstance();
            final int currentDayInt = calendar.get(Calendar.DAY_OF_WEEK);
            final int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
            final int currentMin = calendar.get(Calendar.MINUTE);
            final String currentDayString = getDayString(currentDayInt);

            // append the current day of the week to the URL to only
            // fetch food trucks open on this day
            final String urlString = SF_GOV_URL_PREFIX + "?dayofweekstr=" + currentDayString;

            // fetch the JSON encoded list of FoodTrucks open on the current day from the SF website
            String jsonResponseString = fetchJsonResponseFromURL(urlString);

            // convert to JSON list object of FoodTrucks using GSON parser
            final Gson gson = new Gson();
            final Type collectionType = new TypeToken<Collection<FoodTruck>>(){}.getType();
            final Collection<FoodTruck> todaysFoodTrucks = gson.fromJson(jsonResponseString, collectionType);


            // filter down to trucks open now and then sort alphabetically
            final List<FoodTruck> foodTrucksOpenNow = getFoodTrucksOpenNow(todaysFoodTrucks, currentHour, currentMin, currentDayString);
            Collections.sort(foodTrucksOpenNow);

            // print out in batches of 10, enable printing next batch of 10
            printTrucks(foodTrucksOpenNow);
        } catch (Exception e) {
            System.out.println("Error encountered while generating list of available food trucks: " + e.getMessage());
        }
    }

    // make a HTTP GET request to the given URL string, return HTTP response in String format
    private static String fetchJsonResponseFromURL(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        StringBuilder webResponseStringBuilder = new StringBuilder();
        while ((line = rd.readLine()) != null) {
            webResponseStringBuilder.append(line);
        }
        rd.close();

        return webResponseStringBuilder.toString();
    }

    // filter out any trucks in input collection to just those trucks open right now
    private static List<FoodTruck> getFoodTrucksOpenNow (Collection<FoodTruck> todaysFoodTrucks, int currentHour,
                                                         int currentMin, String currentDayString) {
        final List<FoodTruck> openFoodTrucks = new ArrayList<>();
        for (FoodTruck foodTruck : todaysFoodTrucks) {
            // sanity check to skip any invalid food trucks returned by webservice
            if (foodTruck == null || foodTruck.start24 == null || foodTruck.end24 == null
                    || foodTruck.applicant == null || foodTruck.location == null
                    || foodTruck.dayofweekstr == null || !currentDayString.equals(foodTruck.dayofweekstr)) {
                continue;
            }

            // split out the start and end hours+mins from their string representations
            final String[] splitStartTime = foodTruck.start24.split(":");
            final String[] splitEndTime = foodTruck.end24.split(":");
            // sanity check that the String start and end times were properly formatted
            if (splitStartTime.length < 2 || splitEndTime.length < 2) {
                continue;
            }
            final int startHour = Integer.parseInt(splitStartTime[0]);
            final int startMin = Integer.parseInt(splitStartTime[1]);
            final int endHour = Integer.parseInt(splitEndTime[0]);
            final int endMin = Integer.parseInt(splitEndTime[1]);

            // use the start and end hours+mins to determine
            // if a given truck is open right now
            if (currentHour > startHour && currentHour < endHour) {
                openFoodTrucks.add(foodTruck);
            }
            else if (currentHour == startHour) {
                if (currentMin >= startMin ) {
                    if (startHour != endHour || currentMin < endMin) {
                        openFoodTrucks.add(foodTruck);
                    }
                }
            }
            else if (currentHour == endHour) {
                if (currentMin < endMin ) {
                    if (startHour != endHour || currentMin > startMin) {
                        openFoodTrucks.add(foodTruck);
                    }
                }
            }
        }

        return openFoodTrucks;
    }

    // print FoodTruck list to the screen in batches of 10
    private static void printTrucks(List<FoodTruck> openFoodTrucks) {
        System.out.printf("%-25.25s     %-25.25s \n", "NAME", "ADDRESS");
        int numPagesRemaining = (int) Math.ceil(openFoodTrucks.size()/10.0);
        final int initialNumPages = numPagesRemaining;
        Scanner scan = new Scanner(System.in);
        for (int i=0; i<openFoodTrucks.size(); i+=10) {
            for (int j=i; j<(i+10) && j<openFoodTrucks.size(); j++) {
                final FoodTruck foodTruck = openFoodTrucks.get(j);
                System.out.printf("%-25.25s     %-25.25s \n", foodTruck.applicant, foodTruck.location);
            }
            numPagesRemaining--;
            if (numPagesRemaining > 0) {
                System.out.print("(Page " + (initialNumPages - numPagesRemaining) + " of " + initialNumPages + ") Show next page? Y/N : ");
                final String input = scan.nextLine();
                if (input != null && input.length() > 0 && (input.charAt(0) == 'N' || input.charAt(0) == 'n')) {
                    break;
                }
            }
        }
    }

    // converts the given Calendar day int index to corresponding String
    private static String getDayString(int dayOfWeekIndex) {
        switch (dayOfWeekIndex) {
            case Calendar.SUNDAY:
                return "Sunday";
            case Calendar.MONDAY:
                return "Monday";
            case Calendar.TUESDAY:
                return "Tuesday";
            case Calendar.WEDNESDAY:
                return "Wednesday";
            case Calendar.THURSDAY:
                return "Thursday";
            case Calendar.FRIDAY:
                return "Friday";
            default:
                return "Saturday";
        }
    }

    /**
     * Simple POJO class encapsulating all of the information this program currently needs
     *  to represent an individual food truck
     */
    private class FoodTruck implements Comparable<FoodTruck> {
        private String start24;
        private String end24;
        private String dayofweekstr;
        private String applicant;
        private String location;

        @Override
        // enable sorting alphabetically by name (applicant appears to be the food truck vendor name field)
        public int compareTo(FoodTruck otherTruck) {
            return this.applicant.compareToIgnoreCase(otherTruck.applicant);
        }
    }
}

// to run:
// $ javac FoodTruckFinder.java && java FoodTruckFinder
