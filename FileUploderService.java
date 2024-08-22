package com.example.demo.service;

import com.example.demo.dto.CarrierPlanDTO;
import com.example.demo.entity.CarrierPlan;
import com.example.demo.entity.UploadedFile;
import com.example.demo.repository.CarrierPlanRepository;
import com.example.demo.repository.UploadedFileRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Service
public class FileUploadService {

    @Autowired
    private CarrierPlanRepository carrierPlanRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private Validator validator;

    private static final List<String> EXPECTED_HEADERS = Arrays.asList("carrierid", "plantype", "accountid", "prospect_client");

    public void saveFile(MultipartFile file) throws IOException {
        // Validate the file headers first
        validateFileHeaders(file);

        // Validate the entire file content
        Set<CarrierPlanDTO> carrierPlanDTOs = validateFileContent(file);

        // If validation passes, save the file
        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setFileName(file.getOriginalFilename());
        uploadedFile.setFileData(file.getBytes());
        uploadedFileRepository.save(uploadedFile);

        // Convert DTOs to entities and save
        Set<CarrierPlan> carrierPlans = new HashSet<>();
        for (CarrierPlanDTO dto : carrierPlanDTOs) {
            CarrierPlan carrierPlan = convertToEntity(dto);
            carrierPlans.add(carrierPlan);
        }

        carrierPlanRepository.saveAll(carrierPlans);
    }

    private void validateFileHeaders(MultipartFile file) throws IOException {
        if (file.getOriginalFilename().endsWith(".csv")) {
            validateCsvHeaders(file);
        } else if (file.getOriginalFilename().endsWith(".xlsx")) {
            validateExcelHeaders(file);
        }
    }

    private void validateCsvHeaders(MultipartFile file) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
        String headerLine = br.readLine();
        if (headerLine == null) {
            throw new IllegalArgumentException("File is empty or missing headers");
        }

        String[] headers = headerLine.split(",");
        validateHeaders(headers);
    }

    private void validateExcelHeaders(MultipartFile file) throws IOException {
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
            throw new IllegalArgumentException("Missing or incorrect headers: " + String.join(", ", missingHeaders));
        }
    }

    private Set<CarrierPlanDTO> validateFileContent(MultipartFile file) throws IOException {
        Set<CarrierPlanDTO> carrierPlanDTOs = new HashSet<>();

        if (file.getOriginalFilename().endsWith(".csv")) {
            carrierPlanDTOs = validateCsv(file);
        } else if (file.getOriginalFilename().endsWith(".xlsx")) {
            carrierPlanDTOs = validateExcel(file);
        }

        return carrierPlanDTOs;
    }

    private Set<CarrierPlanDTO> validateCsv(MultipartFile file) throws IOException {
        Set<CarrierPlanDTO> carrierPlanDTOs = new HashSet<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
        br.readLine(); // Skip header line
        String line;
        while ((line = br.readLine()) != null) {
            String[] data = line.split(",");
            CarrierPlanDTO carrierPlanDTO = new CarrierPlanDTO();
            carrierPlanDTO.setCarrierId(data[0]);
            carrierPlanDTO.setPlanType(data[1]);
            carrierPlanDTO.setAccountId(data[2]);
            carrierPlanDTO.setProspectClient(data[3]);

            validateCarrierPlanDTO(carrierPlanDTO);
            carrierPlanDTOs.add(carrierPlanDTO);
        }
        return carrierPlanDTOs;
    }

    private Set<CarrierPlanDTO> validateExcel(MultipartFile file) throws IOException {
        Set<CarrierPlanDTO> carrierPlanDTOs = new HashSet<>();
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Start from the second row to skip the header
            Row row = sheet.getRow(i);
            CarrierPlanDTO carrierPlanDTO = new CarrierPlanDTO();
            carrierPlanDTO.setCarrierId(row.getCell(0).getStringCellValue());
            carrierPlanDTO.setPlanType(row.getCell(1).getStringCellValue());
            carrierPlanDTO.setAccountId(row.getCell(2).getStringCellValue());
            carrierPlanDTO.setProspectClient(row.getCell(3).getStringCellValue());

            validateCarrierPlanDTO(carrierPlanDTO);
            carrierPlanDTOs.add(carrierPlanDTO);
        }
        workbook.close();
        return carrierPlanDTOs;
    }

    private void validateCarrierPlanDTO(CarrierPlanDTO carrierPlanDTO) {
        Set<ConstraintViolation<CarrierPlanDTO>> violations = validator.validate(carrierPlanDTO);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private CarrierPlan convertToEntity(CarrierPlanDTO dto) {
        CarrierPlan carrierPlan = new CarrierPlan();
        carrierPlan.setCarrierId(dto.getCarrierId());
        carrierPlan.setPlanType(dto.getPlanType());
        carrierPlan.setAccountId(dto.getAccountId());
        carrierPlan.setProspectClient(dto.getProspectClient());
        return carrierPlan;
    }

    public void deleteFile(Long fileId) {
        uploadedFileRepository.deleteById(fileId);
    }
}
