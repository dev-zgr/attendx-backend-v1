package com.example.attendxbackendv2.servicelayer.interfaces;

import com.example.attendxbackendv2.presentationlayer.datatransferobjects.LecturerDTO;
import com.example.attendxbackendv2.servicelayer.exceptions.LecturerAlreadyExistException;
import com.example.attendxbackendv2.servicelayer.exceptions.ResourceNotFoundException;

import java.util.List;

public interface LecturerService {
    /**
     * Create department.
     * it may throw LecturerAlreadyExistException if any lecturer registered using the same e-mail
     * ResourceNotFoundException if no such department found with the specified department name
     * @param lecturerDTO  Lecturer DTO to be crated

     */
    void createLecturer(LecturerDTO lecturerDTO) throws LecturerAlreadyExistException, ResourceNotFoundException;

    /**
     * Get all the lecturer for UI presentation it doesn't fetches the details of the lecturer
     * It uses pagination and sorting by instructors first name.
     * @param pageNo page number of the result. See application.properties for the page size
     * @param ascending sorting order
     */
    List<LecturerDTO> getAllLecturers(int pageNo, boolean ascending);

    /**
     * Fetch lecturer details by email string.
     * @param email the email of the requested lecturer
     * @param getDetails the fetch details. If true, fetches the lecturer details. If false, fetches only the email and name
     *                   of the lecturer. Less lecture increases resilience for the application when presenting the data
     *
     * @return the requested LecturerDTO if found
     * @throws ResourceNotFoundException if no such lecturer found with the specified email
     */
    LecturerDTO getLecturerByEmail(String email, boolean getDetails) throws ResourceNotFoundException;

    /**
     * This method updates the existing Lecturer by first fetching it from the DB and updates it to
     * recent changes
     * @param lecturerDTO with e-mail as id and updated fields
     * @return true if update was successful
     * @throws ResourceNotFoundException may be thrown due trying to access non-existing
     * lecturer or trying to change the department of lecturer to non-existing department
     */
    boolean updateLecturer(LecturerDTO lecturerDTO) throws ResourceNotFoundException;

    /**
     * Deletes the lecturer by its email. This method is subject to update currently
     * it breaks the relationship between Department and Lecturer.
     * @param email  the email of the requested lecturer to be deleted
     * @return true if entity deleted successfully, false otherwise
     */
    boolean deleteLecturer(String email);

    /**
     * This method is used to get the total number of pages in the database
     * @return the total number of pages
     */
    Long getPageCount();
}
