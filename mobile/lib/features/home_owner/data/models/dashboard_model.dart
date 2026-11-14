import 'package:freezed_annotation/freezed_annotation.dart';
import '../../domain/entities/dashboard_entities.dart';

part 'dashboard_model.freezed.dart';
part 'dashboard_model.g.dart';

@freezed
class AppointmentClientModel with _$AppointmentClientModel {
  const factory AppointmentClientModel({
    required String id,
    required String name,
    required String phone,
    String? email,
    String? notes,
  }) = _AppointmentClientModel;

  factory AppointmentClientModel.fromJson(Map<String, dynamic> json) =>
      _$AppointmentClientModelFromJson(json);
}

@freezed
class AppointmentServiceModel with _$AppointmentServiceModel {
  const factory AppointmentServiceModel({
    required String id,
    required String name,
    required String category,
    required int durationMinutes,
    required String basePrice,
    @Default('0.00') String depositAmount,
  }) = _AppointmentServiceModel;

  factory AppointmentServiceModel.fromJson(Map<String, dynamic> json) =>
      _$AppointmentServiceModelFromJson(json);
}

@freezed
class AppointmentModel with _$AppointmentModel {
  const factory AppointmentModel({
    required String id,
    required String confirmationCode,
    required AppointmentClientModel client,
    required AppointmentServiceModel service,
    required String barberId,
    required String barberName,
    required String startTime,  // ISO string del backend
    required String endTime,
    required String status,
    required String depositStatus,
    String? depositAmount,
    @Default(0) int rescheduleCount,
    required String createdAt,
  }) = _AppointmentModel;

  factory AppointmentModel.fromJson(Map<String, dynamic> json) =>
      _$AppointmentModelFromJson(json);
}

// ─── Mappers Model → Domain ───────────────────────────────────────────────────

extension AppointmentModelX on AppointmentModel {
  Appointment toDomain() => Appointment(
    id: id,
    confirmationCode: confirmationCode,
    client: client.toDomain(),
    service: service.toDomain(),
    barberId: barberId,
    barberName: barberName,
    startTime: DateTime.parse(startTime),
    endTime: DateTime.parse(endTime),
    status: AppointmentStatusX.fromString(status),
    depositStatus: depositStatus,
    depositAmount: depositAmount,
    rescheduleCount: rescheduleCount,
    createdAt: DateTime.parse(createdAt),
  );
}

extension AppointmentClientModelX on AppointmentClientModel {
  AppointmentClient toDomain() => AppointmentClient(
    id: id, name: name, phone: phone, email: email, notes: notes,
  );
}

extension AppointmentServiceModelX on AppointmentServiceModel {
  AppointmentService toDomain() => AppointmentService(
    id: id, name: name, category: category,
    durationMinutes: durationMinutes,
    basePrice: basePrice, depositAmount: depositAmount,
  );
}
