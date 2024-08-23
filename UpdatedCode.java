package com.businessadmin.service.impl;

import com.businessadmin.entity.CAGProfile;
import com.businessadmin.entity.CAGProfileBulkUpload;
import com.businessadmin.entity.CAGProfileNotes;
import com.businessadmin.exception.CustomApplicationException;
import com.businessadmin.repository.CAGProfileBulkUploadRepository;
import com.businessadmin.repository.CAGProfileRepository;
import com.businessadmin.request.cagProfileDetails.CAGProfileBulkUploadDTO;
import com.businessadmin.request.common.model.SearchInputMetaData;
import com.businessadmin.response.cagProfileDetails.CAGProfileBulkUploadResponse;
import com.businessadmin.response.common.model.ErrorResponse;
import com.businessadmin.response.common.model.SearchOutputMetaData;
import com.businessadmin.service.CAGProfileBulkUploadService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class CAGProfileBulkUploadServiceImpl implements CAGProfileBulkUploadService {

    private static final String CST_ZONE_ID = "CST";

    @Autowired
    private CAGProfileRepository cagProfileRepository;

    @Autowired
    private CAGProfileBulkUploadRepository cagProfileBulkUploadRepository;

    @Autowired
    private Validator validator;

    // Expected headers for the file
    private static final List<String> EXPECTED_HEADERS = Arrays.asList(
            "carrierid", "accountid", "groupid", "plantype", "mailorderpharmacy", 
            "prospectclient", "editmember", "entitlements", "accesserrormessage", "notes"
    );

    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public CAGProfileBulkUploadResponse saveCagProfileByBulkUpload(MultipartFile file, SearchInputMetaData searchInputMetaData) throws IOException {
        logger.info("Start of saveCagProfileByBulkUpload() : CAGProfileBulkUploadServiceImpl, File Name: {}", file.getOriginalFilename());

        // Step 1: Validate file headers
        validateFileHeaders(file);

        // Step 2: Validate file content
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDTOs = validateFileContent(file);

        // Step 3: Process and save profiles
        LocalDateTime cstLocalDateTime = LocalDateTime.now(ZoneId.of(CST_ZONE_ID));
        Set<CAGProfile> cagProfiles = new HashSet<>();
        for (CAGProfileBulkUploadDTO dto : cagProfileBulkUploadDTOs) {
            CAGProfile cagProfile = convertToEntity(dto, searchInputMetaData, Timestamp.valueOf(cstLocalDateTime));
            cagProfiles.add(cagProfile);
        }

        CAGProfileBulkUpload uploadedFile = null;

        try {
            if (!cagProfiles.isEmpty()) {
                cagProfiles.forEach(cagProfile -> {
                    if (cagProfile.getCagProfileNotes() != null) {
                        cagProfile.getCagProfileNotes().forEach(notes -> notes.setCagProfile(cagProfile));
                    }
                });
                cagProfileRepository.saveAll(cagProfiles);
            }

            if (!file.isEmpty()) {
                uploadedFile = CAGProfileBulkUpload.builder()
                        .fileName(file.getOriginalFilename())
                        .fileData(file.getBytes())
                        .userIdCreated(searchInputMetaData.getUserId())
                        .dateTimeCreated(Timestamp.valueOf(cstLocalDateTime))
                        .build();
                cagProfileBulkUploadRepository.save(uploadedFile);
            }
        } catch (Exception ex) {
            logger.error("Error in saveCagProfileByBulkUpload() : CAGProfileBulkUploadServiceImpl", ex);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .endpoint("/cagBulkUpload")
                    .errorTitle(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .errorMessage(ex.getMessage())
                    .correlationId(searchInputMetaData.getCorrelationId())
                    .build();
            throw new CustomApplicationException(errorResponse);
        }

        // Step 4: Prepare and return response
        CAGProfileBulkUploadResponse cagProfileDetailsResponse = new CAGProfileBulkUploadResponse();
        SearchOutputMetaData searchOutputMetaData = SearchOutputMetaData.builder()
                .respCode(String.valueOf(HttpStatus.CREATED.value()))
                .correlationId(searchInputMetaData.getCorrelationId())
                .respMessage(List.of("CAG Profile Details saved successfully"))
                .build();
        cagProfileDetailsResponse.setSearchOutputMetaData(searchOutputMetaData);
        cagProfileDetailsResponse.setCagProfiles(cagProfiles);
        cagProfileDetailsResponse.setFileName(Objects.requireNonNull(uploadedFile).getFileName());
        cagProfileDetailsResponse.setFileType(file.getContentType());
        cagProfileDetailsResponse.setFileSize(String.valueOf(file.getSize()));
        cagProfileDetailsResponse.setFileCreated("File created successfully");

        logger.info("End of saveCagProfileByBulkUpload() : CAGProfileBulkUploadServiceImpl");
        return cagProfileDetailsResponse;
    }

    /**
     * Validates the headers of the uploaded file.
     *
     * @param file Multipart file to be validated
     * @throws IOException If there's an error reading the file
     */
    private void validateFileHeaders(MultipartFile file) throws IOException {
        String fileName = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        if (fileName.endsWith(".csv")) {
            validateCsvFileHeaders(file);
        } else if (fileName.endsWith(".xlsx")) {
            validateXlsxHeaders(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName);
        }
    }

    /**
     * Validates the headers of a CSV file.
     *
     * @param file Multipart file to be validated
     * @throws IOException If there's an error reading the file
     */
    private void validateCsvFileHeaders(MultipartFile file) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("File is empty or missing headers");
            }

            String[] headers = Arrays.stream(headerLine.split(","))
                    .map(String::toLowerCase)
                    .map(String::trim)
                    .toArray(String[]::new);
            validateHeaders(headers);
        }
    }

    /**
     * Validates the headers of an Excel (.xlsx) file.
     *
     * @param file Multipart file to be validated
     * @throws IOException If there's an error reading the file
     */
    private void validateXlsxHeaders(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("File is empty or missing headers");
            }

            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getPhysicalNumberOfCells(); i++) {
                headers.add(headerRow.getCell(i).getStringCellValue().toLowerCase().trim());
            }

            validateHeaders(headers.toArray(new String[0]));
        }
    }

    /**
     * Validates if the headers of the uploaded file match the expected headers.
     *
     * @param headers Array of headers from the uploaded file
     */
    private void validateHeaders(String[] headers) {
        List<String> missingHeaders = new ArrayList<>();
        for (String expectedHeader : EXPECTED_HEADERS) {
            if (!Arrays.asList(headers).contains(expectedHeader)) {
                missingHeaders.add(expectedHeader);
            }
        }
        if (!missingHeaders.isEmpty()) {
            logger.error("Missing or incorrect headers: {}", String.join(", ", missingHeaders));
            throw new IllegalArgumentException("Missing or incorrect headers: " + String.join(", ", missingHeaders));
        }
    }

    /**
     * Validates the content of the uploaded file.
     *
     * @param file Multipart file to be validated
     * @return A set of CAGProfileBulkUploadDTO
     * @throws IOException If there's an error reading the file
     */
    private Set<CAGProfileBulkUploadDTO> validateFileContent(MultipartFile file) throws IOException {
        logger.info("Start of validateFileContent() : CAGProfileBulkUploadServiceImpl");
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDTOs;

        String fileName = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        if (fileName.endsWith(".csv")) {
            cagProfileBulkUploadDTOs = validateCsvFile(file);
        } else if (fileName.endsWith(".xlsx")) {
            cagProfileBulkUploadDTOs = validateXlsxFile(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName);
        }

        logger.info("End of validateFileContent() : CAGProfileBulkUploadServiceImpl");
        return cagProfileBulkUploadDTOs;
    }
/**
     * Validates the content of a CSV file.
     *
     * @param file Multipart file to be validated
     * @return A set of CAGProfileBulkUploadDTO
     * @throws IOException If there's an error reading the file
     */
    private Set<CAGProfileBulkUploadDTO> validateCsvFile(MultipartFile file) throws IOException {
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDtos = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            br.readLine(); // Skip header line
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                CAGProfileBulkUploadDTO cagProfileBulkUpload = createCAGProfileBulkUploadDTO(data);
                validateCAGProfileBulkUploadDTO(cagProfileBulkUpload);
                cagProfileBulkUploadDtos.add(cagProfileBulkUpload);
            }
        }
        return cagProfileBulkUploadDtos;
    }

    /**
     * Validates the content of an Excel (.xlsx) file.
     *
     * @param file Multipart file to be validated
     * @return A set of CAGProfileBulkUploadDTO
     * @throws IOException If there's an error reading the file
     */
    private Set<CAGProfileBulkUploadDTO> validateXlsxFile(MultipartFile file) throws IOException {
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDTOs = new HashSet<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                String[] data = new String[row.getPhysicalNumberOfCells()];
                for (int j = 0; j < row.getPhysicalNumberOfCells(); j++) {
                    data[j] = row.getCell(j).toString().trim();
                }
                CAGProfileBulkUploadDTO cagProfileBulkUpload = createCAGProfileBulkUploadDTO(data);
                validateCAGProfileBulkUploadDTO(cagProfileBulkUpload);
                cagProfileBulkUploadDTOs.add(cagProfileBulkUpload);
            }
        }
        return cagProfileBulkUploadDTOs;
    }

    /**
     * Creates a CAGProfileBulkUploadDTO from the data array.
     *
     * @param data Array of string data from a row in the file
     * @return A CAGProfileBulkUploadDTO
     */
    private CAGProfileBulkUploadDTO createCAGProfileBulkUploadDTO(String[] data) {
        return CAGProfileBulkUploadDTO.builder()
                .carrierId(data[0])
                .accountId(data[1])
                .groupId(data[2])
                .planType(data[3])
                .mailOrderPharmacy(data[4])
                .prospectClient(data[5])
                .editMember(data[6])
                .entitlements(data[7])
                .accessErrorMessage(data[8])
                .notes(data[9])
                .build();
    }

    /**
     * Validates a CAGProfileBulkUploadDTO object using the Validator.
     *
     * @param dto CAGProfileBulkUploadDTO to be validated
     */
    private void validateCAGProfileBulkUploadDTO(CAGProfileBulkUploadDTO dto) {
        Set<ConstraintViolation<CAGProfileBulkUploadDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    /**
     * Converts a CAGProfileBulkUploadDTO to a CAGProfile entity.
     *
     * @param dto CAGProfileBulkUploadDTO to be converted
     * @param searchInputMetaData Search input metadata
     * @param timestamp The current timestamp
     * @return A CAGProfile entity
     */
    private CAGProfile convertToEntity(CAGProfileBulkUploadDTO dto, SearchInputMetaData searchInputMetaData, Timestamp timestamp) {
        return CAGProfile.builder()
                .carrierId(dto.getCarrierId())
                .accountId(dto.getAccountId())
                .groupId(dto.getGroupId())
                .planType(dto.getPlanType())
                .mailOrderPharmacy(dto.getMailOrderPharmacy())
                .prospectClient(dto.getProspectClient())
                .editMember(dto.getEditMember())
                .entitlements(dto.getEntitlements())
                .accessErrorMessage(dto.getAccessErrorMessage())
                .cagProfileNotes(createCAGProfileNotes(dto.getNotes()))
                .userIdCreated(searchInputMetaData.getUserId())
                .dateTimeCreated(timestamp)
                .build();
    }

    /**
     * Creates a set of CAGProfileNotes from the notes string.
     *
     * @param notes The notes string
     * @return A set of CAGProfileNotes
     */
    private Set<CAGProfileNotes> createCAGProfileNotes(String notes) {
        Set<CAGProfileNotes> notesSet = new HashSet<>();
        if (StringUtils.hasText(notes)) {
            CAGProfileNotes cagProfileNotes = CAGProfileNotes.builder()
                    .noteContent(notes)
                    .build();
            notesSet.add(cagProfileNotes);
        }
        return notesSet;
    }
}
