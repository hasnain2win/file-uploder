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
import com.microsoft.applicationinsights.boot.dependencies.apachecommons.lang3.StringUtils;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;


public class CAGProfileBulkUploadServiceImpl implements CAGProfileBulkUploadService {


    private static final String CST_ZONE_ID = "CST";
    @Autowired
    private CAGProfileRepository cagProfileRepository;

    @Autowired
    private CAGProfileBulkUploadRepository cagProfileBulkUploadRepository;

    @Autowired
    private Validator validator;

    private static final List<String> EXPECTED_HEADERS = Arrays.asList("carrierId", "accountId", "groupId", "planType", "mailOrderPharmacy", "prospectClient", "editMember", "entitlements", "accessErrorMessage", "notes");

    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public CAGProfileBulkUploadResponse saveCagProfileByBulkUpload(MultipartFile file, SearchInputMetaData searchInputMetaData) throws IOException {
        logger.info("Start of saveCagProfileByBulkUpload() : CAGProfileBulkUploadServiceImpl");

        validateFileHeaders(file);
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDTO = validateFileContent(file);
        CAGProfileBulkUpload uploadedFile = null;
        Set<CAGProfile> cagProfiles = new HashSet<>();
        TimeZone cstTimeZone = TimeZone.getTimeZone(CST_ZONE_ID);
        LocalDateTime cstLocalDateTime = Timestamp.valueOf(LocalDateTime.now(cstTimeZone.toZoneId())).toLocalDateTime();
        for (CAGProfileBulkUploadDTO dto : cagProfileBulkUploadDTO) {
            CAGProfile cagProfile = convertToEntity(dto, searchInputMetaData, Timestamp.valueOf(cstLocalDateTime));
            cagProfiles.add(cagProfile);
        }
        try {
            if (!cagProfiles.isEmpty()) {
                cagProfiles.forEach(cagProfile -> {
                    if (cagProfile.getCagProfileNotes() != null) {
                        cagProfile.getCagProfileNotes().forEach(notes -> {
                            notes.setCagProfile(cagProfile);
                        });
                    }
                });
                cagProfileRepository.saveAll(cagProfiles);
            }
            if (!file.isEmpty()) {
                new CAGProfileBulkUpload();
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

    private void validateFileHeaders(MultipartFile file) throws IOException {
        if (Objects.requireNonNull(file.getOriginalFilename()).endsWith(".csv")) {
            validateCsvFileHeaders(file);
        } else if (file.getOriginalFilename().endsWith(".xlsx")) {
            validateXlsxHeaders(file);
        }
    }

    private void validateCsvFileHeaders(MultipartFile file) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
        String headerLine = br.readLine();
        if (headerLine == null) {
            throw new IllegalArgumentException("File is empty or missing headers");
        }

        String[] headers = headerLine.split(",");
        validateHeaders(headers);
    }

    private void validateXlsxHeaders(MultipartFile file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            workbook.close();
            throw new IllegalArgumentException("File is empty or missing headers");
        }

        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getPhysicalNumberOfCells(); i++) {
            headers.add(headerRow.getCell(i).getStringCellValue().toLowerCase().trim());
        }

        workbook.close();
        validateHeaders(headers.toArray(new String[0]));
    }

    private void validateHeaders(String[] headers) {
        List<String> missingHeaders = new ArrayList<>();
        for (String expectedHeader : EXPECTED_HEADERS) {
            if (!Arrays.asList(headers).contains(expectedHeader)) {
                missingHeaders.add(expectedHeader);
            }
        }
        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException("Missing or incorrect headers: "
                    + String.join(", ", missingHeaders));
        }
    }

    private Set<CAGProfileBulkUploadDTO> validateFileContent(MultipartFile file) throws IOException {
        logger.info("Start of validateFileContent() : CAGProfileBulkUploadServiceImpl");
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUpload = new HashSet<>();

        if (Objects.requireNonNull(file.getOriginalFilename()).endsWith(".csv")) {
            cagProfileBulkUpload = validatedCsvFile(file);
        } else if (file.getOriginalFilename().endsWith(".xlsx")) {
            cagProfileBulkUpload = validateXlsxFile(file);
        }
        logger.info("End of validateFileContent() : CAGProfileBulkUploadServiceImpl");
        return cagProfileBulkUpload;
    }

    private Set<CAGProfileBulkUploadDTO> validatedCsvFile(MultipartFile file) throws IOException {
        logger.info("Start of validatedCsvFile() : CAGProfileBulkUploadServiceImpl");
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDtos = new HashSet<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
        br.readLine();
        String line;
        while ((line = br.readLine()) != null) {
            String[] data = line.split(",");
            CAGProfileBulkUploadDTO cagProfileBulkUpload = new CAGProfileBulkUploadDTO();
            cagProfileBulkUpload.setCarrierId(data[0]);
            cagProfileBulkUpload.setAccountId(data[1]);
            cagProfileBulkUpload.setGroupId(data[2]);
            cagProfileBulkUpload.setPlanType(data[3]);
            cagProfileBulkUpload.setMailOrderPharmacy(data[4]);
            cagProfileBulkUpload.setProspectClient(data[5]);
            cagProfileBulkUpload.setEditMember(data[6]);
            cagProfileBulkUpload.setEntitlements(data[7]);
            cagProfileBulkUpload.setAccessErrorMessage(data[8]);
            cagProfileBulkUpload.setNotes(data[9]);

            validateCAGProfileBulkUploadDTO(cagProfileBulkUpload);
            cagProfileBulkUploadDtos.add(cagProfileBulkUpload);
        }
        return cagProfileBulkUploadDtos;
    }

    private Set<CAGProfileBulkUploadDTO> validateXlsxFile(MultipartFile file) throws IOException {
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDTOs = new HashSet<>();
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            CAGProfileBulkUploadDTO cagProfileBulkUpload = new CAGProfileBulkUploadDTO();
            cagProfileBulkUpload.setCarrierId(row.getCell(0).getStringCellValue());
            cagProfileBulkUpload.setAccountId(row.getCell(1).getStringCellValue());
            cagProfileBulkUpload.setGroupId(row.getCell(2).getStringCellValue());
            cagProfileBulkUpload.setPlanType(row.getCell(3).getStringCellValue());
            cagProfileBulkUpload.setMailOrderPharmacy(row.getCell(4).getStringCellValue());
            cagProfileBulkUpload.setProspectClient(row.getCell(5).getStringCellValue());
            cagProfileBulkUpload.setEditMember(row.getCell(6).getStringCellValue());
            cagProfileBulkUpload.setEntitlements(row.getCell(7).getStringCellValue());
            cagProfileBulkUpload.setAccessErrorMessage(row.getCell(8).getStringCellValue());
            cagProfileBulkUpload.setNotes(row.getCell(9).getStringCellValue());

            validateCAGProfileBulkUploadDTO(cagProfileBulkUpload);
            cagProfileBulkUploadDTOs.add(cagProfileBulkUpload);
        }
        workbook.close();
        return cagProfileBulkUploadDTOs;
    }

    private void validateCAGProfileBulkUploadDTO(CAGProfileBulkUploadDTO bulkUploadDTO) {
        Set<ConstraintViolation<CAGProfileBulkUploadDTO>> violations = validator.validate(bulkUploadDTO);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private CAGProfile convertToEntity(CAGProfileBulkUploadDTO dto, SearchInputMetaData searchInputMetaData, Timestamp cstLocalDateTime) {

        CAGProfile cagProfile = new CAGProfile();
        cagProfile.setCarrierId(dto.getCarrierId());
        cagProfile.setPlanType(dto.getPlanType());
        cagProfile.setAccountId(dto.getAccountId());
        cagProfile.setProspectClient(dto.getProspectClient());
        cagProfile.setMailOrderPharmacy(dto.getMailOrderPharmacy());
        cagProfile.setEditMember(dto.getEditMember());
        cagProfile.setAccessRole(dto.getEntitlements());
        cagProfile.setAccessErrorMesg(dto.getAccessErrorMessage());
        cagProfile.setUserIdCreated(searchInputMetaData.getUserId());
        cagProfile.setDateTimeCreated(cstLocalDateTime);
        if (!StringUtils.isEmpty(dto.getNotes())) {
            CAGProfileNotes cagProfileNotes = new CAGProfileNotes();
            cagProfileNotes.setNotes(dto.getNotes());
            cagProfileNotes.setUserIdCreated(searchInputMetaData.getUserId());
            cagProfileNotes.setDateTimeCreated(cstLocalDateTime);
            cagProfileNotes.setEffectiveDate(cstLocalDateTime);
            cagProfile.addCAGProfileNotes(cagProfileNotes);
        }

        return cagProfile;
    }

    public void deleteFile(Integer fileId) {
        cagProfileBulkUploadRepository.deleteById(fileId);
    }
}

