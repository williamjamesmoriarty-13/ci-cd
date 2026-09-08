from marshmallow import Schema, fields, validate


class StudentCreateSchema(Schema):
    first_name = fields.String(required=True, validate=validate.Length(min=1, max=80))
    last_name = fields.String(required=True, validate=validate.Length(min=1, max=80))
    email = fields.Email(required=True, validate=validate.Length(max=120))


class StudentUpdateSchema(Schema):
    first_name = fields.String(validate=validate.Length(min=1, max=80))
    last_name = fields.String(validate=validate.Length(min=1, max=80))
    email = fields.Email(validate=validate.Length(max=120))
