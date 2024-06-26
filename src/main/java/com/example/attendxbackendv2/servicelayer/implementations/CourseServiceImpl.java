package com.example.attendxbackendv2.servicelayer.implementations;

import com.example.attendxbackendv2.datalayer.entities.*;
import com.example.attendxbackendv2.datalayer.repositories.*;
import com.example.attendxbackendv2.presentationlayer.datatransferobjects.CourseDTO;
import com.example.attendxbackendv2.presentationlayer.datatransferobjects.UserBaseDTO;
import com.example.attendxbackendv2.servicelayer.exceptions.CourseAlreadyExistsException;
import com.example.attendxbackendv2.servicelayer.exceptions.InvalidCredentialsException;
import com.example.attendxbackendv2.servicelayer.exceptions.ResourceNotFoundException;
import com.example.attendxbackendv2.servicelayer.exceptions.StudentAlreadyEnrolledException;
import com.example.attendxbackendv2.servicelayer.interfaces.CourseService;
import com.example.attendxbackendv2.servicelayer.interfaces.LoginService;
import com.example.attendxbackendv2.servicelayer.mappers.CourseMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class CourseServiceImpl implements CourseService {

    @Value("${pagination.size}")
    private int pageSize;
    private final LecturerRepository lecturerRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final SessionRepository sessionRepository;
    private final LoginService loginService;

    @Autowired
    public CourseServiceImpl(LecturerRepository lecturerRepository, DepartmentRepository departmentRepository, CourseRepository courseRepository, StudentRepository studentRepository, SessionRepository sessionRepository, LoginService loginService) {
        this.lecturerRepository = lecturerRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.loginService = loginService;
    }


    @Override
    @Transactional
    public void createCourse(CourseDTO courseDTO) throws ResourceNotFoundException, CourseAlreadyExistsException {
        LecturerEntity lecturer = lecturerRepository.findLecturerEntityByEmailIgnoreCase(courseDTO.getLecturerEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer", "email", courseDTO.getLecturerEmail()));
        DepartmentEntity department = departmentRepository.findByDepartmentNameIgnoreCase(courseDTO.getDepartmentName())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "name", courseDTO.getDepartmentName()));
        CourseEntity courseEntity = CourseMapper.mapToCourseEntity(new CourseEntity(), courseDTO);
        courseEntity.setDepartment(department);
        department.addCourse(courseEntity);
        lecturer.addCourse(courseEntity);
        courseEntity.setLecturer(lecturer);
        List<SessionEntity> courseSessions = generateCourseSessions(courseEntity);
        courseEntity.setCourseSessions(courseSessions);
        courseRepository.save(courseEntity);
        sessionRepository.saveAll(courseSessions);
        lecturerRepository.save(lecturer);
        departmentRepository.save(department);
    }

    @Override
    @Transactional
    public List<CourseDTO> getAllCourses(int pageNo, boolean ascending, String token) {
        String loginRole = loginService.validateToken(token);
        Pageable pageable;
        if(loginRole.equalsIgnoreCase("EDITOR") || loginRole.equalsIgnoreCase("STUDENT")){
            if (ascending) {
                pageable = PageRequest.of(pageNo, pageSize, Sort.by("courseCode").ascending());
            } else {
                pageable = PageRequest.of(pageNo, pageSize, Sort.by("courseCode").descending());
            }
            List<CourseEntity> courseEntities = courseRepository.findAll(pageable).getContent();
            return courseEntities.stream().map(courseEntity -> CourseMapper.mapToCourseDTO(courseEntity, new CourseDTO(), false)).toList();
        } else {
            UserBaseDTO userBaseEntity = loginService.getUserByToken(UUID.fromString(token));
            LecturerEntity lecturer = lecturerRepository.findLecturerEntityByEmailIgnoreCase(userBaseEntity.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Lecturer", "email",userBaseEntity.getEmail()));
            if (ascending) {
                pageable = PageRequest.of(pageNo, pageSize, Sort.by("courseCode").ascending());
            } else {
                pageable = PageRequest.of(pageNo, pageSize, Sort.by("courseCode").descending());
            }
            List<CourseEntity> courseEntities = courseRepository.findAllByLecturer(pageable,lecturer).getContent();
            return courseEntities.stream().map(courseEntity -> CourseMapper.mapToCourseDTO(courseEntity, new CourseDTO(), false)).toList();
        }

    }

    @Override
    @Transactional
    public CourseDTO getCourseByCourseCode(String courseCode, boolean getDetails, String token) throws ResourceNotFoundException, InvalidCredentialsException {
        String loginRole = loginService.validateToken(token);
        Pageable pageable;

        CourseEntity course = courseRepository.findCourseEntityByCourseCodeIgnoreCase(courseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "courseCode", courseCode));
        if(loginRole.equalsIgnoreCase("STUDENT")){
            CourseDTO courseDTO =  CourseMapper.mapToCourseDTO(course, new CourseDTO(), getDetails);
            courseDTO.setCourseSessions(null);
            courseDTO.setEnrolledStudents(null);
            return courseDTO;
        }else if(loginRole.equalsIgnoreCase("LECTURER")){
            UserBaseDTO userBaseEntity = loginService.getUserByToken(UUID.fromString(token));
            LecturerEntity lecturer = lecturerRepository.findLecturerEntityByEmailIgnoreCase(userBaseEntity.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Lecturer", "email",userBaseEntity.getEmail()));
            if(lecturer.getCourses().stream().anyMatch(lecturerCourse -> lecturerCourse.getCourseCode().equalsIgnoreCase(courseCode))){
                return CourseMapper.mapToCourseDTO(course, new CourseDTO(), getDetails);
            }else{
                throw new InvalidCredentialsException("Invalid Token");
            }
        }else{
            return CourseMapper.mapToCourseDTO(course, new CourseDTO(), getDetails);
        }
    }

    @Override
    @Transactional
    public boolean updateCourse(CourseDTO courseDTO, String token){
        boolean isUpdated = false;
        //First find course
        CourseEntity courseToUpdate = courseRepository.findCourseEntityByCourseCodeIgnoreCase(courseDTO.getCourseCode())
                .orElseThrow(() -> new ResourceNotFoundException("Course",
                        "courseCode",
                        courseDTO.getCourseCode()));

        UserBaseDTO userBaseEntity = loginService.getUserByToken(UUID.fromString(token));
        if(!courseToUpdate.getLecturer().getEmail().equalsIgnoreCase(userBaseEntity.getEmail()) && userBaseEntity.getRole().equalsIgnoreCase("LECTURER")){
            throw new InvalidCredentialsException("Invalid Token");
        }
        // Then find department by department name if not found then throw exception
        DepartmentEntity oldDepartment = departmentRepository.findByDepartmentNameIgnoreCase(courseToUpdate.getDepartment().getDepartmentName())
                .orElseThrow(() -> new ResourceNotFoundException("Department",
                        "departmentName",
                        courseDTO.getDepartmentName()));
        // Then find lecturer by email if not found then throw exception
        LecturerEntity oldLecturer = lecturerRepository.findLecturerEntityByEmailIgnoreCase(courseToUpdate.getLecturer().getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer",
                        "email",
                        courseDTO.getLecturerEmail()));

        // Then Find The students the course entity
        Set<StudentEntity> oldStudents = new HashSet<>(
                courseToUpdate.getEnrolledStudents().stream().map(student -> studentRepository.findStudentEntityByStudentId(student.getStudentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Student", "email", student.getEmail()))).toList()
        );

        //First Update the changed meta fields inside the course entity not the relationships
        CourseMapper.mapToCourseEntity(courseToUpdate, courseDTO);


        DepartmentEntity newDepartment = departmentRepository.findByDepartmentNameIgnoreCase(courseDTO.getDepartmentName())
                .orElseThrow(() -> new ResourceNotFoundException("Department",
                        "departmentName",
                        courseDTO.getDepartmentName()));

        LecturerEntity newLecturer = lecturerRepository.findLecturerEntityByEmailIgnoreCase(courseDTO.getLecturerEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer",
                        "email",
                        courseDTO.getLecturerEmail()));

        Set<StudentEntity> newStudents = new HashSet<>(courseDTO.getEnrolledStudents().stream().map(student -> studentRepository.findStudentEntityByStudentId(student.getStudentNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Student", "email", student.getEmail()))).toList());

        try {
            if (!Objects.equals(oldDepartment.getDepartmentId(), newDepartment.getDepartmentId())) {
                //get rid of the old department
                oldDepartment.removeCourse(courseToUpdate);
                departmentRepository.save(oldDepartment);

                courseToUpdate.setDepartment(newDepartment);
                courseRepository.save(courseToUpdate);

                newDepartment.addCourse(courseToUpdate);
                departmentRepository.save(newDepartment);
            }
            if (!Objects.equals(oldLecturer.getUserId(), newLecturer.getUserId())) {
                //get rid of the old lecturer
                oldLecturer.removeCourse(courseToUpdate);
                lecturerRepository.save(oldLecturer);

                courseToUpdate.setLecturer(newLecturer);
                courseRepository.save(courseToUpdate);

                newLecturer.addCourse(courseToUpdate);
                lecturerRepository.save(newLecturer);
            }

            Set<StudentEntity> studentsToRemove = new HashSet<>(oldStudents);
            studentsToRemove.removeAll(newStudents);

            Set<StudentEntity> studentsToAdd = new HashSet<>(newStudents);
            studentsToAdd.removeAll(oldStudents);

            for (StudentEntity student : studentsToRemove) {
                student.unrollFromCourse(courseToUpdate);
                studentRepository.save(student);

                courseToUpdate.unrollStudent(student);
                courseRepository.save(courseToUpdate);
            }

            for (StudentEntity student : studentsToAdd) {
                courseToUpdate.enrollStudent(student);
                courseRepository.save(courseToUpdate);
                student.enrollToCourse(courseToUpdate);
                studentRepository.save(student);
            }

        } finally {
            courseRepository.save(courseToUpdate);

        }

        isUpdated = true;
        return isUpdated;
    }

    @Override
    @Transactional
    public boolean enrollStudent(String courseCode, String studentId) throws ResourceNotFoundException {
        boolean isEnrolled = false;
        CourseEntity course = courseRepository.findCourseEntityByCourseCodeIgnoreCase(courseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "courseCode", courseCode));
        StudentEntity student = studentRepository.findStudentEntityByStudentId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "studentId", studentId));
        if(course.getEnrolledStudents().stream().anyMatch(studentEntity -> studentEntity.getStudentId().equalsIgnoreCase(studentId))){
            throw new StudentAlreadyEnrolledException(studentId, courseCode);
        }
        course.enrollStudent(student);
        courseRepository.save(course);
        student.enrollToCourse(course);
        studentRepository.save(student);
        isEnrolled = true;
        return isEnrolled;
    }

    @Transactional
    public List<SessionEntity> generateCourseSessions(CourseEntity course) {
        List<SessionEntity> sessions = new ArrayList<>();
        LocalDate currentDate = course.getStartDate();
        while (currentDate.isBefore(course.getEndDate())) {
            SessionEntity session = new SessionEntity();
            session.setSessionDate(currentDate);
            session.setCourse(course);
            sessions.add(session);
            currentDate = currentDate.plusDays(7);
        }
        return sessions;
    }

    @Override
    public Long getPageCount() {
        return (lecturerRepository.count() + pageSize - 1) / pageSize;
    }
}
