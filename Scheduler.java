package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;
    private static int AppointmentID = 1;

    public static void main(String[] args) throws SQLException {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");
        System.out.println("> reserve <date> <vaccine>");
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");
        System.out.println("> logout");
        System.out.println("> quit");
        System.out.println();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        // create_patient <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsPatient(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        // check 3: check if password is strong enough
        if (!isStrongPassword(password)) {
            System.out.println("Password is not strong enough. Include mixture of upper and lowercase letters, " +
                    "a number, and a special character (“!”, “@”, “#”, “?”)");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the patient
        try {
            currentPatient = new Patient.PatientBuilder(username, salt, hash).build();
            // save to patient information to our database
            currentPatient.saveToDB();
            System.out.println(" *** Account created successfully *** ");
        } catch (SQLException e) {
            System.out.println("Create failed");
            e.printStackTrace();
        }
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        // check 3: check if password is strong enough
        if (!isStrongPassword(password)) {
            System.out.println("Password is not strong enough. Include mixture of upper and lowercase letters, " +
                                "a number, and a special character (“!”, “@”, “#”, “?”)");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            currentCaregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build();
            // save to caregiver information to our database
            currentCaregiver.saveToDB();
            System.out.println(" *** Account created successfully *** ");
        } catch (SQLException e) {
            System.out.println("Create failed");
            e.printStackTrace();
        }
    }

    public static boolean isStrongPassword (String password)
    {
        // Checking lower alphabet in string
        int n = password.length();
        boolean hasLower = false, hasUpper = false, hasDigit = false, specialChar = false;
        Set<Character> set = new HashSet<> (Arrays.asList('!', '@', '#', '?'));
        for (char i : password.toCharArray())
        {
            if (Character.isLowerCase(i))
                hasLower = true;
            if (Character.isUpperCase(i))
                hasUpper = true;
            if (Character.isDigit(i))
                hasDigit = true;
            if (set.contains(i))
                specialChar = true;
        }

        // Check to see if password meets all conditions
        if ((n >= 8) && hasLower && hasUpper && hasDigit && specialChar) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        // login_patient <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("Already logged-in!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when logging in");
            e.printStackTrace();
        }
        // check if the login was successful
        if (patient == null) {
            System.out.println("Please try again!");
        } else {
            System.out.println("Patient logged in as: " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("Already logged-in!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when logging in");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Please try again!");
        } else {
            System.out.println("Caregiver logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        // search_caregiver_schedule <date>
        // check 1: check if the current logged-in user is a caregiver or patient
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login as a caregiver or patient!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            getCaregiverSchedule(date, true);
            getVaccines();
            System.out.println("Caregiver Schedule Displayed!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date! (Format YYYY-MM-DD)");
        } catch (SQLException e) {
            System.out.println("Error occurred when searching for caregiver schedule!");
            e.printStackTrace();
        }
    }

    private static ArrayList<String> getCaregiverSchedule(String date, boolean print) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getCaregiver = "SELECT Username FROM Availabilities WHERE Time = ?";
        try {
            PreparedStatement statement = con.prepareStatement(getCaregiver);
            statement.setString(1, date);
            ResultSet resultSet = statement.executeQuery();
            if (print) {
                System.out.println("The available caregivers are:");
            }
            ArrayList<String> usernames = new ArrayList<>();
            while (resultSet.next()) {
                String username = resultSet.getString("Username");
                usernames.add(username);
                if (print) {
                    System.out.println(username);
                }
            }
            return usernames;
        } catch (SQLException e) {
            System.out.println(e);
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    private static void getVaccines() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getVaccine = "Select Name, Doses FROM Vaccines";
        try {
            PreparedStatement statement = con.prepareStatement(getVaccine);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String name = resultSet.getString("Name");
                String doses = resultSet.getString("Doses");
                System.out.println("There are " + doses + " doses of the " + name + " vaccine available!");
            }
        } catch (SQLException e) {
            System.out.println(e);
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    private static void reserve(String[] tokens) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        // reserve <date> <vaccine>
        // check 1: check if the current logged-in user is a patient
        if (currentPatient == null) {
            System.out.println("Please login as a patient!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        String vaccine = tokens[2];

        try {
            Date d = Date.valueOf(date);
            // Check if vaccine exists in system
            if (!checkVaccine(vaccine)) {
                return;
            }
            ArrayList<String> caregivers = getCaregiverSchedule(date, false);
            // Check if caregiver is available
            if (caregivers.size() == 0) {
                System.out.print("There are no caregivers available for your selected date!");
                return;
            }
            String caregiver = caregivers.get(0);
            uploadAppointment(AppointmentID, vaccine, d, currentPatient.getUsername(), caregiver);
            removeAvailability(caregiver, d);
            removeDoses(vaccine);
            System.out.println("Reservation " + AppointmentID + " made with " + caregiver + "!");
            AppointmentID++;

        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid reservation date! (Format YYYY-MM-DD)");
        }
    }

    private static boolean checkVaccine(String vaccine) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getVaccine = "SELECT Name, Doses FROM Vaccines WHERE Name = ?";

        try {
            PreparedStatement statement = con.prepareStatement(getVaccine);
            statement.setString(1, vaccine);
            ResultSet resultSet = statement.executeQuery();
            int count = 0;
            while (resultSet.next()) {
                int doses = resultSet.getInt("Doses");
                if (doses == 0) {
                    System.out.println("There are 0 doses of this vaccine available!");
                    return false;
                }
                count++;
            }
            if (count > 0) {
                return true;
            } else {
                System.out.println("Please enter a valid vaccine!");
                return false;
            }
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    private static void removeAvailability(String caregiver, Date d) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getVaccine = "DELETE FROM Availabilities WHERE Username = ? AND Time = ?";
        try {
            PreparedStatement statement = con.prepareStatement(getVaccine);
            statement.setString(1, caregiver);
            statement.setDate(2, d);
            statement.execute();
        } catch (SQLException e) {
            System.out.println(e);
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    private static void uploadAppointment(int ID, String Vaccine_Name, Date d, String Patient_Name,
                                          String Caregiver_Name) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String addAppointment = "INSERT INTO Appointments VALUES (? , ? , ? , ? , ?)";
        try {
            PreparedStatement statement = con.prepareStatement(addAppointment);
            statement.setInt(1, ID);
            statement.setString(2, Vaccine_Name);
            statement.setDate(3, d);
            statement.setString(4, Patient_Name);
            statement.setString(5, Caregiver_Name);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e);
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }
    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability. Date may already be uploaded.");
           // e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        // TODO: Extra credit
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void removeDoses(String vaccineName) {
        // remove_doses
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when removing doses");
            e.printStackTrace();
        }
        try {
            vaccine.decreaseAvailableDoses(1);
        } catch (SQLException e) {
            System.out.println("Error occurred when decreasing doses");
            e.printStackTrace();
        }
    }

    private static void showAppointments(String[] tokens)  throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        // show_appointments
        // check 1: check if the current logged-in user is a caregiver or patient
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login as a caregiver or patient!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 1) {
            System.out.println("Please try again!");
            return;
        }
        if (currentCaregiver != null) {
            getAppointmentCaregiver();
        } else {
            getAppointmentPatient();
        }
    }

    private static void getAppointmentCaregiver() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getAppointment = "SELECT ID, Vaccine_Name, Time, Patient_Name FROM Appointments WHERE Caregiver_Name = ?";
        try {
            PreparedStatement statement = con.prepareStatement(getAppointment);
            statement.setString(1, currentCaregiver.getUsername());
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String ID = resultSet.getString("ID");
                String Vaccine_Name = resultSet.getString("Vaccine_Name");
                Date Time = resultSet.getDate("Time");
                String Patient_Name = resultSet.getString("Patient_Name");
                System.out.println(Patient_Name + " is scheduled on " + Time + " to receive a " + Vaccine_Name +
                                    " vaccine as per Appointment #" + ID + ".");
            }
        } catch (SQLException e) {
            System.out.println(e);
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    private static void getAppointmentPatient() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String getAppointment = "SELECT ID, Vaccine_Name, Time, Caregiver_Name FROM Appointments WHERE Patient_Name = ?";
        try {
            PreparedStatement statement = con.prepareStatement(getAppointment);
            statement.setString(1, currentPatient.getUsername());
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String ID = resultSet.getString("ID");
                String Vaccine_Name = resultSet.getString("Vaccine_Name");
                Date Time = resultSet.getDate("Time");
                String Caregiver_Name = resultSet.getString("Caregiver_Name");
                System.out.println("You are scheduled on " + Time + " to receive a " + Vaccine_Name +
                        " vaccine as per Appointment #" + ID + " from " + Caregiver_Name + ".");
            }
        } catch (SQLException e) {
            System.out.println(e);
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    private static void logout(String[] tokens) {
        // logout
        //check 1: check if the user is logged in
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("User is not logged in!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 1) {
            System.out.println("Please try again!");
            return;
        }
        if (currentCaregiver != null) {
            currentCaregiver = null;
        } else {
            currentPatient = null;
        }
        System.out.println("You have been logged out");
    }
}